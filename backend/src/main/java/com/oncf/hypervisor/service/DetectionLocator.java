package com.oncf.hypervisor.service;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Places each detection where the object actually stands, rather than where
 * its camera is bolted.
 *
 * <p>Until this existed, every event from a given camera was stamped with
 * that camera's surveyed position — the only location the backend knew. It
 * is a reasonable default and it is what makes zone matching work, but it
 * has a consequence that is easy to miss: with one camera, <em>every</em>
 * detection lands on exactly the same point. Any rule that reasons about
 * distance is then comparing a point with itself, and silently degenerates:
 *
 * <ul>
 *     <li>"how far is this person from the rails?" — a constant, so the rule
 *     either always fires or never does</li>
 *     <li>"how far did they wander?" — always zero, so a trajectory model
 *     sees a stationary dot no matter what the person did</li>
 *     <li>"is anyone standing near that bag?" — collapses into "did this
 *     camera see any person recently", anywhere in frame</li>
 * </ul>
 *
 * <p>The detector now measures where each object stands relative to the
 * centre of the camera's view, in metres (see {@code scene_geometry.py}),
 * and reports it as {@code offsetM: [right, away]}. This class converts
 * that local offset into a real latitude/longitude by walking out from the
 * camera's position along the camera's heading. Two people in one frame
 * finally get two different coordinates, and every distance-based rule
 * starts measuring something real — without any of them having to change.
 *
 * <p>Accuracy is metre-scale and depends on the detector's monocular depth
 * estimate, which is fine: the rules distinguish 2 m from 20 m, not 2.0 m
 * from 2.1 m. When a camera reports no offset — an older detector, or a
 * non-YOLO source — the camera's own position is used exactly as before.
 */
@Component
public class DetectionLocator {

    /** Matches {@code "offsetM":[1.25,-3.4]} — metres right of, and beyond, view centre. */
    private static final Pattern OFFSET = Pattern.compile(
            "\"offsetM\"\\s*:\\s*\\[\\s*(-?[0-9]*\\.?[0-9]+)\\s*,\\s*(-?[0-9]*\\.?[0-9]+)\\s*]");

    private static final double METERS_PER_DEG_LAT = 111_320.0;
    /** Beyond this the "offset" is not a plausible in-frame measurement; ignore it. */
    private static final double MAX_PLAUSIBLE_OFFSET_M = 500.0;

    /** A located detection. {@code offsetApplied} is false when the camera's own position was kept. */
    public record Located(double latitude, double longitude, boolean offsetApplied) {}

    /**
     * @param cameraLat  the camera's surveyed latitude
     * @param cameraLon  the camera's surveyed longitude
     * @param headingDeg bearing the camera faces, clockwise from north; null = assume north
     * @param rawPayload the detector's raw payload, which may carry {@code offsetM}
     */
    public Located locate(double cameraLat, double cameraLon, Double headingDeg, String rawPayload) {
        double[] offset = parseOffset(rawPayload);
        if (offset == null) {
            return new Located(cameraLat, cameraLon, false);
        }
        double right = offset[0];
        double away = offset[1];

        // Rotate the camera-relative offset (right, away) into (east, north).
        // At heading 0 the camera looks north, so "away" is north and "right"
        // is east; every other heading is that pair turned clockwise by it.
        double heading = Math.toRadians(headingDeg != null ? headingDeg : 0.0);
        double sin = Math.sin(heading);
        double cos = Math.cos(heading);
        double east = right * cos + away * sin;
        double north = away * cos - right * sin;

        double latitude = cameraLat + north / METERS_PER_DEG_LAT;
        // Metres per degree of longitude shrinks towards the poles.
        double metersPerDegLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(cameraLat));
        double longitude = Math.abs(metersPerDegLon) < 1.0
                ? cameraLon                       // at the poles longitude is meaningless
                : cameraLon + east / metersPerDegLon;

        return new Located(latitude, longitude, true);
    }

    private static double[] parseOffset(String rawPayload) {
        if (rawPayload == null) return null;
        Matcher m = OFFSET.matcher(rawPayload);
        if (!m.find()) return null;
        try {
            double right = Double.parseDouble(m.group(1));
            double away = Double.parseDouble(m.group(2));
            if (!Double.isFinite(right) || !Double.isFinite(away)) return null;
            if (Math.abs(right) > MAX_PLAUSIBLE_OFFSET_M || Math.abs(away) > MAX_PLAUSIBLE_OFFSET_M) {
                return null;
            }
            return new double[]{right, away};
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
