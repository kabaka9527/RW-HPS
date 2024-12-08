#!/bin/bash

# 脚本标题
echo "RW-HPS Linux 脚本"

relyon="wget sudo tar curl jq "

# 定义安装命令的映射
install_commands() {
    case "$OS" in
        "ubuntu"|"debian")
            sudo apt-get install $relyon -y || { echo "请手动安装以下依赖: $relyon"; exit 1; }
            ;;
        "centos"|"fedora")
            sudo yum install $relyon -y || { echo "请手动安装以下依赖: $relyon"; exit 1; }
            ;;
        "arch")
            sudo pacman -S $relyon || { echo "请手动安装以下依赖: $relyon"; exit 1; }
            ;;
        "alpine")
            sudo apk add $relyon -y || { echo "请手动安装以下依赖: $relyon"; exit 1; }
            ;;
        *)
            echo "Error: Unsupported operating system."
            exit 1
            ;;
    esac
}

# 获取当前系统的名称
get_os_name() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
    else
        OS=$(uname -s | tr '[:upper:]' '[:lower:]')
    fi
}

# 安装 wget 和 unzip
install_wget_unzip() {
    get_os_name
    install_commands
}

# 执行安装
install_wget_unzip

# GitHub API 获取最新Pre-release版本
get_latest_pre_release() {
    latest_pre_release=$(curl -s https://api.github.com/repos/RW-HPS/RW-HPS/releases | jq -c '.[] | select(.prerelease == true) | .tag_name' | head -n 1)
    LATEST_PRERELEASE_VERSION=$(echo "$latest_pre_release" | tr -d '"')
}

# 获取当前系统信息
get_system_info() {
    OS_TYPE=$(uname | tr '[:upper:]' '[:lower:]')
    ARCH=$(uname -m)
    if [ "$ARCH" == "x86_64" ]; then
        ARCH="amd64"
    elif [ "$ARCH" == "aarch64" ]; then
        ARCH="arm64"
    fi
}

# 构造Zulu JDK 21下载URL
construct_zulu_url() {
    ZULU_URL="https://api.azul.com/zulu/download/community/v1.0/bundles/latest/?jdk_version=21&bundle_type=jdk&ext=tar.gz"
    if [ "$OS_TYPE" == "linux" ]; then
        ZULU_URL="${ZULU_URL}&os=${OS_TYPE}&arch=${ARCH}"
    else
        echo "当前操作系统不支持自动下载Zulu JDK。"
        exit 1
    fi
}

DOWNLOAD_URL="  https://mirror.ghproxy.com/https://github.com/RW-HPS/RW-HPS/releases/download"

# 下载 RH-HPS
download_rhhps() {
    get_latest_pre_release
    if [ -z "$LATEST_PRERELEASE_VERSION" ]; then
        echo "没有找到最新的Pre-release版本。"
        exit 1
    fi
    echo "正在下载 RH-HPS 最新Pre-release版本 ($LATEST_PRERELEASE_VERSION)..."
    wget -O Server-All.jar "$DOWNLOAD_URL/$LATEST_PRERELEASE_VERSION/Server-All.jar"
    echo "下载完成。"
}

# 安装 Zulu JDK 21
install_zulu_jdk() {
    get_system_info
    construct_zulu_url
    echo "正在下载 Zulu JDK 21..."
    wget -O zulu-jdk21.tar.gz "$ZULU_URL"
    echo "下载完成。"

    # 创建目录
    sudo mkdir -p /opt/RW-HPS_JDK
    # 解压 JDK 到指定目录
    sudo tar -vxf zulu-jdk21.tar.gz -C /opt/RW-HPS_JDK
    # 创建符号链接
    sudo ln -s /opt/RW-HPS_JDK/bin/java /usr/bin/hpsjdk
    # 添加到环境变量
  
check_java_version() {
    java_version_output=$(java -version 2>&1)
    if [[ $java_version_output =~ "21" ]]; then
        echo "Java21或更高版本已安装，继续执行脚本。"
        return 0
    else
        echo "Java 版本不符合要求，请反馈或检查"
        exit 1
    fi
}

# 执行Java版本检查
check_java_version
  
}

# 卸载 Zulu JDK 21
uninstall_zulu_jdk() {
    # 删除符号链接
    sudo rm -f /usr/bin/hpsjdk
    # 删除JDK目录
    sudo rm -rf /opt/RW-HPS_JDK
    # 删除环境变量文件
    echo "Zulu JDK 21 卸载完成。"
}

# 菜单选项
show_menu() {
    echo "请选择一个操作:"
    echo "1. 下载 RH-HPS (默认版本为最新的Pre-release)"
    echo "2. 更新 RH-HPS"
    echo "3. 安装 Zulu JDK 21 并下载最新版 RH-HPS"
    echo "4. 卸载 Zulu JDK 21"
    echo "5. 退出"
    read -p "请输入选项 [1-5]: " option
}

# 主程序
main() {
    show_menu
    case $option in
        1)
            download_rhhps
            ;;
        2)
            update_rhhps
            ;;
        3)
            install_zulu_jdk
            ;;
        4)
            uninstall_zulu_jdk
            ;;
        5)
            echo "退出脚本。"
            exit 0
            ;;
        *)
            echo "无效的选项，请输入 1 到 5 的数字。"
            ;;
    esac
}

# 执行主程序
main