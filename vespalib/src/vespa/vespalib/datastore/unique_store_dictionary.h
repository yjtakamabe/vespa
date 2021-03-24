// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/btree/btree.h>
#include "i_unique_store_dictionary.h"

#pragma once

namespace vespalib::datastore {

class EntryComparatorWrapper;

class NoHashDictionary;

template <typename BTreeDictionaryT>
class UniqueStoreBTreeDictionaryBase
{
protected:
    BTreeDictionaryT _btree_dict;
public:
    static constexpr bool has_btree_dictionary = true;
    UniqueStoreBTreeDictionaryBase()
        : _btree_dict()
    {
    }
};

template <typename HashDictionaryT>
class UniqueStoreHashDictionaryBase
{
protected:
    HashDictionaryT _hash_dict;
public:
    static constexpr bool has_hash_dictionary = true;
    UniqueStoreHashDictionaryBase(std::unique_ptr<EntryComparator> compare)
        : _hash_dict(std::move(compare))
    {
    }
};

template <>
class UniqueStoreHashDictionaryBase<NoHashDictionary>
{
public:
    static constexpr bool has_hash_dictionary = false;
    UniqueStoreHashDictionaryBase(std::unique_ptr<EntryComparator>)
    {
    }
};

/**
 * A dictionary for unique store. Mostly accessed via base class.
 */
template <typename BTreeDictionaryT, typename ParentT = IUniqueStoreDictionary, typename HashDictionaryT = NoHashDictionary>
class UniqueStoreDictionary : public ParentT, public UniqueStoreBTreeDictionaryBase<BTreeDictionaryT>, public UniqueStoreHashDictionaryBase<HashDictionaryT> {
protected:
    using BTreeDictionaryType = BTreeDictionaryT;
    using DataType = typename BTreeDictionaryType::DataType;
    using FrozenView = typename BTreeDictionaryType::FrozenView;
    using ReadSnapshot = typename ParentT::ReadSnapshot;
    using generation_t = typename ParentT::generation_t;

    class ReadSnapshotImpl : public ReadSnapshot {
    private:
        FrozenView _frozen_view;

    public:
        ReadSnapshotImpl(FrozenView frozen_view);
        size_t count(const EntryComparator& comp) const override;
        size_t count_in_range(const EntryComparator& low, const EntryComparator& high) const override;
        void foreach_key(std::function<void(EntryRef)> callback) const override;
    };

public:
    using UniqueStoreBTreeDictionaryBase<BTreeDictionaryT>::has_btree_dictionary;
    using UniqueStoreHashDictionaryBase<HashDictionaryT>::has_hash_dictionary;
    UniqueStoreDictionary(std::unique_ptr<EntryComparator> compare);
    ~UniqueStoreDictionary() override;
    void freeze() override;
    void transfer_hold_lists(generation_t generation) override;
    void trim_hold_lists(generation_t firstUsed) override;
    UniqueStoreAddResult add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry) override;
    EntryRef find(const EntryComparator& comp) override;
    void remove(const EntryComparator& comp, EntryRef ref) override;
    void move_entries(ICompactable& compactable) override;
    uint32_t get_num_uniques() const override;
    vespalib::MemoryUsage get_memory_usage() const override;
    void build(vespalib::ConstArrayRef<EntryRef>, vespalib::ConstArrayRef<uint32_t> ref_counts, std::function<void(EntryRef)> hold) override;
    void build(vespalib::ConstArrayRef<EntryRef> refs) override;
    void build_with_payload(vespalib::ConstArrayRef<EntryRef>, vespalib::ConstArrayRef<uint32_t> payloads) override;
    std::unique_ptr<ReadSnapshot> get_read_snapshot() const override;
    bool get_has_hash_dictionary() const override;
};

}
