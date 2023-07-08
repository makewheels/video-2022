# 构建下载YouTube的自定义Docker镜像

## 定义 Dockerfile

```dockerfile
FROM centos:7

RUN yum install -y java-11-openjdk python3 python3-pip ffmpeg clang gcc
RUN python3 --version
RUN pip3 install -i https://mirrors.aliyun.com/pypi/simple/ yt-dlp
```

## 构建自定义镜像

```shell
docker build -t video-2022-youtube-base-image:1.0 .
```

## 删除所有镜像

```shell
docker stop $(docker ps -aq)
docker rm $(docker ps -aq)
docker rmi --force $(docker images -q)
```

## 推送到阿里云Docker镜像仓库

登录香港仓库

```shell
sudo docker login --username=finalbird@foxmail.com registry.cn-hongkong.aliyuncs.com
```

打标签 tag

```shell
docker tag video-2022-youtube-base-image:1.0 registry.cn-hongkong.aliyuncs.com/b4/video-2022-youtube-base-image:1.0
```

推送

```shell
docker push registry.cn-hongkong.aliyuncs.com/b4/video-2022-youtube-base-image:1.0
```