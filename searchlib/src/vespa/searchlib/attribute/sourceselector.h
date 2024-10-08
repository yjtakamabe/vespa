// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributememorysavetarget.h"
#include "attributevector.h"
#include <vespa/searchlib/queryeval/isourceselector.h>

namespace search {

class SourceSelector : public queryeval::ISourceSelector
{
private:
protected:
    AttributeVector::SP _realSource;

    queryeval::Source getNewSource(queryeval::Source src, uint32_t diff) {
        return src > diff ? src - diff : 0;
    }

public:
    struct HeaderInfo {
        std::string _baseFileName;
        queryeval::Source _defaultSource;
        uint32_t _baseId;
        uint32_t _docIdLimit;
        HeaderInfo(const std::string & baseFileName,
                   queryeval::Source defaultSource,
                   uint32_t baseId,
                   uint32_t docIdLimit);
    };

    class SaveInfo {
    private:
        HeaderInfo _header;
        AttributeMemorySaveTarget _memSaver;
    public:
        using UP = std::unique_ptr<SaveInfo>;
        using SP = std::shared_ptr<SaveInfo>;
        SaveInfo(const std::string & baseFileName,
                 queryeval::Source defaultSource,
                 uint32_t baseId,
                 uint32_t docIdLimit,
                 AttributeVector & sourceStore);
        ~SaveInfo();
        const HeaderInfo & getHeader() const { return _header; }
        bool save(const TuneFileAttributes &tuneFileAttributes,
                  const search::common::FileHeaderContext &fileHeaderContext);
    };

    class LoadInfo {
    private:
        HeaderInfo _header;
    public:
        using UP = std::unique_ptr<LoadInfo>;
        LoadInfo(const std::string & baseFileName);
        void load();
        const HeaderInfo & header() const { return _header; }
    };

    class Histogram {
    public:
        Histogram() { memset(_h, 0, sizeof(_h)); }
        uint32_t operator [] (queryeval::Source s) const { return _h[s]; }
        void inc(queryeval::Source s) { _h[s]++; }
    private:
        uint32_t _h[256];
    };

public:
    using UP = std::unique_ptr<SourceSelector>;
    SourceSelector(queryeval::Source defaultSource, AttributeVector::SP realSource);
    /**
     * This will compute the distribution of the sources used over the whole lid space.
     */
    Histogram getDistribution() const;
    SaveInfo::UP extractSaveInfo(const std::string & baseFileName);
    static LoadInfo::UP extractLoadInfo(const std::string & baseFileName);

    void setSource(uint32_t docId, queryeval::Source source) override = 0;
    std::unique_ptr<queryeval::sourceselector::Iterator> createIterator() const override = 0;
};

} // namespace search

