// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/source.h>
#include <vespa/config/common/types.h>

namespace config {

class FileSpec;
class DirSpec;
class IConfigHolder;

class FileSource : public Source
{
private:
    std::shared_ptr<IConfigHolder> _holder;
    const std::string _fileName;
    int64_t _lastLoaded;
    int64_t _generation;

    StringVector readConfigFile(const std::string & fileName);
    int64_t getLast(const std::string & fileName);

public:
    FileSource(std::shared_ptr<IConfigHolder> holder, const std::string & fileName);
    FileSource(const FileSource &) = delete;
    FileSource & operator = (const FileSource &) = delete;
    ~FileSource() override;
    void getConfig() override;
    void close() override;
    void reload(int64_t generation) override;
};

} // namespace config

