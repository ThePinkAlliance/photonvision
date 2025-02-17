/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.targeting;

import java.util.ArrayList;
import java.util.List;
import org.photonvision.common.dataflow.structures.Packet;

/** Represents a pipeline result from a PhotonCamera. */
public class PhotonPipelineResult {
    private static boolean HAS_WARNED = false;

    // Targets to store.
    public final List<PhotonTrackedTarget> targets = new ArrayList<>();

    // Latency in milliseconds.
    private double latencyMillis;

    // Timestamp in milliseconds.
    private double timestampSeconds = -1;

    // Multi-tag result
    private MultiTargetPNPResult multiTagResult = new MultiTargetPNPResult();

    /** Constructs an empty pipeline result. */
    public PhotonPipelineResult() {}

    /**
     * Constructs a pipeline result.
     *
     * @param latencyMillis The latency in the pipeline.
     * @param targets The list of targets identified by the pipeline.
     */
    public PhotonPipelineResult(double latencyMillis, List<PhotonTrackedTarget> targets) {
        this.latencyMillis = latencyMillis;
        this.targets.addAll(targets);
    }

    /**
     * Constructs a pipeline result.
     *
     * @param latencyMillis The latency in the pipeline.
     * @param targets The list of targets identified by the pipeline.
     * @param result Result from multi-target PNP.
     */
    public PhotonPipelineResult(
            double latencyMillis, List<PhotonTrackedTarget> targets, MultiTargetPNPResult result) {
        this.latencyMillis = latencyMillis;
        this.targets.addAll(targets);
        this.multiTagResult = result;
    }

    /**
     * Returns the size of the packet needed to store this pipeline result.
     *
     * @return The size of the packet needed to store this pipeline result.
     */
    public int getPacketSize() {
        return targets.size() * PhotonTrackedTarget.PACK_SIZE_BYTES
                + 8 // latency
                + MultiTargetPNPResult.PACK_SIZE_BYTES
                + 1; // target count
    }

    /**
     * Returns the best target in this pipeline result. If there are no targets, this method will
     * return null. The best target is determined by the target sort mode in the PhotonVision UI.
     *
     * @return The best target of the pipeline result.
     */
    public PhotonTrackedTarget getBestTarget() {
        if (!hasTargets() && !HAS_WARNED) {
            String errStr =
                    "This PhotonPipelineResult object has no targets associated with it! Please check hasTargets() "
                            + "before calling this method. For more information, please review the PhotonLib "
                            + "documentation at http://docs.photonvision.org";
            System.err.println(errStr);
            new Exception().printStackTrace();
            HAS_WARNED = true;
        }
        return hasTargets() ? targets.get(0) : null;
    }

    /**
     * Returns the latency in the pipeline.
     *
     * @return The latency in the pipeline.
     */
    public double getLatencyMillis() {
        return latencyMillis;
    }

    /**
     * Returns the estimated time the frame was taken, This is more accurate than using <code>
     * getLatencyMillis()</code>
     *
     * @return The timestamp in seconds, or -1 if this result has no timestamp set.
     */
    public double getTimestampSeconds() {
        return timestampSeconds;
    }

    /**
     * Sets the FPGA timestamp of this result in seconds.
     *
     * @param timestampSeconds The timestamp in seconds.
     */
    public void setTimestampSeconds(double timestampSeconds) {
        this.timestampSeconds = timestampSeconds;
    }

    /**
     * Returns whether the pipeline has targets.
     *
     * @return Whether the pipeline has targets.
     */
    public boolean hasTargets() {
        return !targets.isEmpty();
    }

    /**
     * Returns a copy of the vector of targets.
     *
     * @return A copy of the vector of targets.
     */
    public List<PhotonTrackedTarget> getTargets() {
        return new ArrayList<>(targets);
    }

    /**
     * Return the latest multi-target result. Be sure to check
     * getMultiTagResult().estimatedPose.isPresent before using the pose estimate!
     */
    public MultiTargetPNPResult getMultiTagResult() {
        return multiTagResult;
    }

    /**
     * Populates the fields of the pipeline result from the packet.
     *
     * @param packet The incoming packet.
     * @return The incoming packet.
     */
    public Packet createFromPacket(Packet packet) {
        // Decode latency, existence of targets, and number of targets.
        latencyMillis = packet.decodeDouble();
        this.multiTagResult = MultiTargetPNPResult.createFromPacket(packet);
        byte targetCount = packet.decodeByte();

        targets.clear();

        // Decode the information of each target.
        for (int i = 0; i < (int) targetCount; ++i) {
            var target = new PhotonTrackedTarget();
            target.createFromPacket(packet);
            targets.add(target);
        }

        return packet;
    }

    /**
     * Populates the outgoing packet with information from this pipeline result.
     *
     * @param packet The outgoing packet.
     * @return The outgoing packet.
     */
    public Packet populatePacket(Packet packet) {
        // Encode latency, existence of targets, and number of targets.
        packet.encode(latencyMillis);
        multiTagResult.populatePacket(packet);
        packet.encode((byte) targets.size());

        // Encode the information of each target.
        for (var target : targets) target.populatePacket(packet);

        // Return the packet.
        return packet;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + targets.hashCode();
        long temp;
        temp = Double.doubleToLongBits(latencyMillis);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(timestampSeconds);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + ((multiTagResult == null) ? 0 : multiTagResult.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PhotonPipelineResult other = (PhotonPipelineResult) obj;
        if (!targets.equals(other.targets)) return false;
        if (Double.doubleToLongBits(latencyMillis) != Double.doubleToLongBits(other.latencyMillis))
            return false;
        if (Double.doubleToLongBits(timestampSeconds)
                != Double.doubleToLongBits(other.timestampSeconds)) return false;
        if (multiTagResult == null) {
            if (other.multiTagResult != null) return false;
        } else if (!multiTagResult.equals(other.multiTagResult)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "PhotonPipelineResult [targets="
                + targets
                + ", latencyMillis="
                + latencyMillis
                + ", timestampSeconds="
                + timestampSeconds
                + ", multiTagResult="
                + multiTagResult
                + "]";
    }
}
