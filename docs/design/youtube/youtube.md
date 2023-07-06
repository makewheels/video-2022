构建自定义镜像

```dockerfile
FROM centos:7

RUN yum install -y java-11-openjdk python3 python3-pip ffmpeg clang gcc
RUN python3 --version
RUN pip3 install yt-dlp

RUN pip3 install -i https://mirrors.aliyun.com/pypi/simple/ yt-dlp


```