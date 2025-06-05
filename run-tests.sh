#!/bin/bash

# 用户行为数据收集系统测试运行脚本
# 使用方法: ./run-tests.sh [test-type]
# test-type: unit, integration, performance, coverage, all

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_message() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# 检查Docker是否运行
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        print_message $RED "❌ Docker is not running. Please start Docker first."
        exit 1
    fi
}

# 运行单元测试
run_unit_tests() {
    print_message $BLUE "🧪 Running Unit Tests..."
    mvn clean test -Dspring.profiles.active=test
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "✅ Unit tests passed!"
    else
        print_message $RED "❌ Unit tests failed!"
        exit 1
    fi
}

# 运行集成测试
run_integration_tests() {
    print_message $BLUE "🔗 Running Integration Tests..."
    check_docker
    
    mvn clean verify -Dspring.profiles.active=integration-test
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "✅ Integration tests passed!"
    else
        print_message $RED "❌ Integration tests failed!"
        exit 1
    fi
}

# 运行性能测试
run_performance_tests() {
    print_message $BLUE "⚡ Running Performance Tests..."
    
    mvn clean test -Dtest="*PerformanceTest" -Dspring.profiles.active=performance-test
    
    if [ $? -eq 0 ]; then
        print_message $GREEN "✅ Performance tests completed!"
    else
        print_message $RED "❌ Performance tests failed!"
        exit 1
    fi
}

# 运行代码覆盖率测试
run_coverage_tests() {
    print_message $BLUE "📊 Running Code Coverage Tests..."
    
    mvn clean verify -Dspring.profiles.active=test
    mvn jacoco:report
    
    # 检查覆盖率
    if [ -f "target/site/jacoco/index.html" ]; then
        COVERAGE=$(grep -o 'Total.*[0-9]\+%' target/site/jacoco/index.html | grep -o '[0-9]\+%' | head -1 | sed 's/%//')
        print_message $YELLOW "📈 Code coverage: ${COVERAGE}%"
        
        if [ "$COVERAGE" -ge 70 ]; then
            print_message $GREEN "✅ Code coverage meets threshold (≥70%)"
        else
            print_message $RED "❌ Code coverage below threshold (<70%)"
            exit 1
        fi
        
        print_message $BLUE "📄 Coverage report: target/site/jacoco/index.html"
    else
        print_message $RED "❌ Coverage report not generated!"
        exit 1
    fi
}

# 运行所有测试
run_all_tests() {
    print_message $BLUE "🚀 Running All Tests..."
    
    run_unit_tests
    run_integration_tests
    run_coverage_tests
    run_performance_tests
    
    print_message $GREEN "🎉 All tests completed successfully!"
}

# 清理测试环境
cleanup() {
    print_message $YELLOW "🧹 Cleaning up test environment..."
    
    # 停止可能运行的测试容器
    docker stop $(docker ps -q --filter "name=testcontainers") 2>/dev/null || true
    docker rm $(docker ps -aq --filter "name=testcontainers") 2>/dev/null || true
    
    # 清理Maven缓存
    mvn clean
    
    print_message $GREEN "✅ Cleanup completed!"
}

# 显示帮助信息
show_help() {
    echo "用户行为数据收集系统测试运行脚本"
    echo ""
    echo "使用方法:"
    echo "  ./run-tests.sh [test-type]"
    echo ""
    echo "测试类型:"
    echo "  unit         - 运行单元测试"
    echo "  integration  - 运行集成测试"
    echo "  performance  - 运行性能测试"
    echo "  coverage     - 运行代码覆盖率测试"
    echo "  all          - 运行所有测试"
    echo "  cleanup      - 清理测试环境"
    echo "  help         - 显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  ./run-tests.sh unit"
    echo "  ./run-tests.sh all"
    echo ""
}

# 检查参数
if [ $# -eq 0 ]; then
    show_help
    exit 1
fi

# 根据参数执行相应的测试
case $1 in
    unit)
        run_unit_tests
        ;;
    integration)
        run_integration_tests
        ;;
    performance)
        run_performance_tests
        ;;
    coverage)
        run_coverage_tests
        ;;
    all)
        run_all_tests
        ;;
    cleanup)
        cleanup
        ;;
    help)
        show_help
        ;;
    *)
        print_message $RED "❌ Unknown test type: $1"
        show_help
        exit 1
        ;;
esac 