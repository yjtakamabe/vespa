// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/tensor/cell_values.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/partial_update.h>
#include <vespa/eval/tensor/sparse/sparse_tensor.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> modify_layouts = {
    {x({"a"})},                           {x({"a"})},
    {x({"a",""})},                        {x({"b","c","d","e"})},
    {x(5)},                               {x({"1","2","foo","17"})},
    {x({"a","b","c"}),y({"d","e"})},      {x({"b"}),y({"d"})},             
    {x({"a","b","c"})},                   {x({"b","c","d"})},
    {x(3),y(2)},                          {x({"0","1"}),y({"0","1"})},
    {x({"a","","b"})},                    {x({""})}
};

TensorSpec::Address sparsify(const TensorSpec::Address &input) {
    TensorSpec::Address output;
    for (const auto & kv : input) {
        if (kv.second.is_indexed()) {
            auto val = fmt("%zu", kv.second.index);
            output.emplace(kv.first, val);
        } else {
            output.emplace(kv.first, kv.second);
        }
    }
    return output;
}

TensorSpec reference_modify(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    TensorSpec result(a.type());
    auto end_iter = b.cells().end();
    for (const auto &cell: a.cells()) {
        double v = cell.second;
        auto sparse_addr = sparsify(cell.first);
        auto iter = b.cells().find(sparse_addr);
        if (iter == end_iter) {
            result.add(cell.first, v);
        } else {
            result.add(cell.first, fun(v, iter->second));
        }
    }
    return result;
}

Value::UP try_partial_modify(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    return tensor::TensorPartialUpdate::modify(*lhs, fun, *rhs, factory);
}

TensorSpec perform_partial_modify(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    auto up = try_partial_modify(a, b, fun);
    if (up) {
        return spec_from_value(*up);
    } else {
        return TensorSpec(a.type());
    }
}

TensorSpec perform_old_modify(const TensorSpec &a, const TensorSpec &b, join_fun_t fun) {
    const auto &engine = tensor::DefaultTensorEngine::ref();
    auto lhs = engine.from_spec(a);
    auto rhs = engine.from_spec(b);
    auto lhs_tensor = dynamic_cast<tensor::Tensor *>(lhs.get());
    EXPECT_TRUE(lhs_tensor);
    auto rhs_sparse = dynamic_cast<tensor::SparseTensor *>(rhs.get());
    EXPECT_TRUE(rhs_sparse);
    tensor::CellValues cell_values(*rhs_sparse);
    auto up = lhs_tensor->modify(fun, cell_values);
    EXPECT_TRUE(up);
    return engine.to_spec(*up);
}


TEST(PartialModifyTest, partial_modify_works_for_simple_values) {
    ASSERT_TRUE((modify_layouts.size() % 2) == 0);
    for (size_t i = 0; i < modify_layouts.size(); i += 2) {
        TensorSpec lhs = spec(modify_layouts[i], N());
        TensorSpec rhs = spec(modify_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        for (auto fun: {operation::Add::f, operation::Mul::f, operation::Sub::f}) {
            auto expect = reference_modify(lhs, rhs, fun);
            auto actual = perform_partial_modify(lhs, rhs, fun);
            EXPECT_EQ(actual, expect);
        }
        auto fun = [](double, double keep) { return keep; };
        auto expect = reference_modify(lhs, rhs, fun);
        auto actual = perform_partial_modify(lhs, rhs, fun);
        EXPECT_EQ(actual, expect);
    }
}

TEST(PartialModifyTest, partial_modify_works_like_old_modify) {
    ASSERT_TRUE((modify_layouts.size() % 2) == 0);
    for (size_t i = 0; i < modify_layouts.size(); i += 2) {
        TensorSpec lhs = spec(modify_layouts[i], N());
        TensorSpec rhs = spec(modify_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        for (auto fun: {operation::Add::f, operation::Mul::f, operation::Sub::f}) {
            auto expect = perform_old_modify(lhs, rhs, fun);
            auto actual = perform_partial_modify(lhs, rhs, fun);
            EXPECT_EQ(actual, expect);
            if (fun == operation::Max::f) {
                printf("%s modify(sub) %s -> %s\n", lhs.to_string().c_str(), rhs.to_string().c_str(), actual.to_string().c_str());
            }
        }
    }
}

std::vector<Layout> bad_layouts = {
    {x(3)},                               {x(3)},
    {x(3),y({"a"})},                      {x(3),y({"a"})},
    {x({"a"})},                           {x({"a"}),y({"b"})},
    {x({"a"}),y({"b"})},                  {x({"a"})},
    {x({"a"})},                           {x({"a"}),y(1)}
};

TEST(PartialModifyTest, partial_modify_returns_nullptr_on_invalid_inputs) {
    ASSERT_TRUE((bad_layouts.size() % 2) == 0);
    for (size_t i = 0; i < bad_layouts.size(); i += 2) {
        TensorSpec lhs = spec(bad_layouts[i], N());
        TensorSpec rhs = spec(bad_layouts[i + 1], Div16(N()));
        SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
        for (auto fun: {operation::Add::f}) {
            auto actual = try_partial_modify(lhs, rhs, fun);
            auto expect = Value::UP();
            EXPECT_EQ(actual, expect);
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
