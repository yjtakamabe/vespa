// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/eval/eval/value.h>
#include "mutable_dense_tensor_view.h"

namespace search::tensor { class ITensorAttribute; }
namespace search::features {

/**
 * Executor for extracting dense tensors from an underlying dense tensor attribute
 * without copying cells data.
 */
class DenseTensorAttributeExecutor : public fef::FeatureExecutor
{
private:
    const search::tensor::ITensorAttribute& _attribute;
    vespalib::eval::MutableDenseTensorView _tensorView;

public:
    DenseTensorAttributeExecutor(const search::tensor::ITensorAttribute& attribute);
    void execute(uint32_t docId) override;
};

}
