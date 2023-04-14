// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

import com.yahoo.config.provision.DockerImage;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A partial container, containing only fields returned by a container list command such as 'podman ps'.
 *
 * @author mpolden
 */
public class PartialContainer {

    private final ContainerId id;
    private final ContainerName name;
    private final Instant createdAt;
    private final State state;
    private final String imageId;
    private final DockerImage image;
    private final Map<String, String> labels;
    private final int pid;
    private final boolean managed;

    public PartialContainer(ContainerId id, ContainerName name, Instant createdAt, State state, String imageId,
                            DockerImage image, Map<String, String> labels, int pid, boolean managed) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.state = Objects.requireNonNull(state);
        this.imageId = Objects.requireNonNull(imageId);
        this.image = Objects.requireNonNull(image);
        this.labels = Map.copyOf(Objects.requireNonNull(labels));
        this.pid = pid;
        this.managed = managed;
    }

    /** A unique identifier for this. Typically generated by the container engine */
    public ContainerId id() {
        return id;
    }

    /** The given name of this */
    public ContainerName name() {
        return name;
    }

    /** Timestamp when this container was created */
    public Instant createdAt() {
        return createdAt;
    }

    /** Current state of this */
    public State state() {
        return state;
    }

    /** A unique identifier for the image in use by this */
    public String imageId() {
        return imageId;
    }

    /** The image in use by this */
    public DockerImage image() {
        return image;
    }

    /** The labels set on this */
    public Map<String, String> labels() {
        return labels;
    }

    /** The PID of this */
    public int pid() {
        return pid;
    }

    /** Returns whether this container is managed by node-admin */
    public boolean managed() {
        return managed;
    }

    /** Returns the value of given label key */
    public String label(String key) {
        String labelValue = labels.get(key);
        if (labelValue == null) throw new IllegalArgumentException("No such label '" + key + "'");
        return labelValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialContainer that = (PartialContainer) o;
        return pid == that.pid && managed == that.managed && id.equals(that.id) && name.equals(that.name) && createdAt.equals(that.createdAt) && state == that.state && imageId.equals(that.imageId) && image.equals(that.image) && labels.equals(that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, createdAt, state, imageId, image, labels, pid, managed);
    }

    /** The state of a container */
    public enum State {

        unknown,
        configured,
        created,
        running,
        stopped,
        paused,
        exited,
        removing,
        stopping;

        public boolean isRunning() {
            return this == running;
        }

        public static Container.State from(String state) {
            return switch (state) {
                case "unknown" -> unknown;
                case "configured" -> configured;
                case "created" -> created;
                case "running" -> running;
                case "stopped" -> stopped;
                case "paused" -> paused;
                case "exited" -> exited;
                case "removing" -> removing;
                case "stopping" -> stopping;
                default -> throw new IllegalArgumentException("Invalid state '" + state + "'");
            };
        }

    }

}
