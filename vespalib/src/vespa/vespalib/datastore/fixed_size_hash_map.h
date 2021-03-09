// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_entry_ref.h"
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <limits>
#include <atomic>
#include <deque>
#include <functional>

namespace vespalib { class GenerationHolder; }
namespace vespalib::datastore {

class EntryComparator;

/*
 * Fixed sized hash map over keys in data store.
 */
class FixedSizeHashMap {
public:
    static constexpr uint32_t no_node_idx = std::numeric_limits<uint32_t>::max();
    using KvType = std::pair<AtomicEntryRef, AtomicEntryRef>;
    using generation_t = GenerationHandler::generation_t;
    using sgeneration_t = GenerationHandler::sgeneration_t;

private:
    class ChainHead {
        std::atomic<uint32_t> _node_idx;

    public:
        ChainHead()
            : _node_idx(no_node_idx)
        {
        }
        // Writer thread
        uint32_t load_relaxed() const noexcept { return _node_idx.load(std::memory_order_relaxed); }
        void set(uint32_t node_idx) { _node_idx.store(node_idx, std::memory_order_release); }

        // Reader thread
        uint32_t load_acquire() const noexcept { return _node_idx.load(std::memory_order_acquire); }
    };
    class Node {
        KvType _kv;
        std::atomic<uint32_t> _next;
    public:
        Node()
            : Node(std::make_pair(AtomicEntryRef(), AtomicEntryRef()), no_node_idx)
        {
        }
        Node(KvType kv, uint32_t next)
            : _kv(kv),
              _next(next)
        {
        }
        Node(Node &&rhs); // Must be defined, but must never be used.
        void on_free();
        std::atomic<uint32_t>& get_next() noexcept { return _next; }
        const std::atomic<uint32_t>& get_next() const noexcept { return _next; }
        KvType& get_kv() noexcept { return _kv; }
        const KvType& get_kv() const noexcept { return _kv; }
    };

    Array<ChainHead>  _chain_heads;
    Array<Node>       _nodes;
    uint32_t          _modulo;
    uint32_t          _count;
    uint32_t          _free_head;
    uint32_t          _free_count;
    uint32_t          _hold_count;
    Array<uint32_t>   _hold_1_list;
    std::deque<std::pair<generation_t, uint32_t>> _hold_2_list;
    uint32_t          _num_stripes;

    void transfer_hold_lists_slow(generation_t generation);
    void trim_hold_lists_slow(generation_t usedGen);
    void force_add(const EntryComparator& comp, const KvType& kv);
public:
    FixedSizeHashMap(uint32_t module, uint32_t capacity, uint32_t num_stripes);
    FixedSizeHashMap(uint32_t module, uint32_t capacity, uint32_t num_stripes, const FixedSizeHashMap &orig, const EntryComparator& comp);
    ~FixedSizeHashMap();

    KvType& add(const EntryComparator& comp, std::function<EntryRef(void)>& insert_entry);
    KvType* remove(const EntryComparator& comp, EntryRef key_ref);
    const KvType* find(const EntryComparator& comp, EntryRef key_ref) const;

    void transfer_hold_lists(generation_t generation) {
        if (!_hold_1_list.empty()) {
            transfer_hold_lists_slow(generation);
        }
    }

    void trim_hold_lists(generation_t usedGen) {
        if (!_hold_2_list.empty() && static_cast<sgeneration_t>(_hold_2_list.front().first - usedGen) < 0) {
            trim_hold_lists_slow(usedGen);
        }
    }

    bool full() const noexcept { return _nodes.size() == _nodes.capacity() && _free_count == 0u; }
    size_t size() const noexcept { return _count; }
};

}
