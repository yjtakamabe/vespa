// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.Optional;

/**
 * Common implementation for Item classes implementing the TaggableItem interface.
 * Note that this file exist in 3 copies that should be kept in sync:
 *
 * CompositeTaggableItem.java
 * SimpleTaggableItem.java
 * TaggableSegmentItem.java
 *
 * These should only have trivial differences.
 * (multiple inheritance or mixins would have been nice).
 *
 * @author arnej27959
 */
public abstract class SimpleTaggableItem extends Item implements TaggableItem {

    public int getUniqueID() {
        return uniqueID;
    }

    public void setUniqueID(int id) {
        setHasUniqueID(true);
        uniqueID = id;
    }

    @Override
    public void setConnectivity(Item item, double connectivity) {
        if (!(item instanceof TaggableItem)) {
            throw new IllegalArgumentException("setConnectivity item must be taggable, was: "
                                               + item.getClass() + " [" + item + "]");
        }
        setHasUniqueID(true);
        item.setHasUniqueID(true);
        if (connectedItem != null) {
            // untangle old connectivity
            connectedItem.connectedBacklink = null;
        }
        this.connectivity = connectivity;
        connectedItem = item;
        connectedItem.connectedBacklink = this;
    }

    public Item getConnectedItem() {
        return connectedItem;
    }

    public double getConnectivity() {
        return connectivity;
    }

    public void setSignificance(double significance) {
        setHasUniqueID(true);
        setExplicitSignificance(true);
        this.significance = significance;
    }

    public void setExplicitSignificance(boolean explicitSignificance) {
        this.explicitSignificance = explicitSignificance;
    }

    public boolean hasExplicitSignificance() {
        return explicitSignificance;
    }

    public double getSignificance() {
        return significance;
    }

    @Override
    public void setDocumentFrequency(DocumentFrequency documentFrequency) { this.documentFrequency = documentFrequency; }

    @Override
    public Optional<DocumentFrequency> getDocumentFrequency() { return Optional.ofNullable(documentFrequency); }

    //Change access privilege from protected to public.
    public boolean hasUniqueID() {
        return super.hasUniqueID();
    }

}
