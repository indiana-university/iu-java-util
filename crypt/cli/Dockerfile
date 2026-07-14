FROM eclipse-temurin:25-alpine
RUN apk update && \
	apk add --no-cache bash
ADD target/iu-java-crypt-cli-bin.tar.gz /opt/crypt
ENV PATH=/opt/crypt/bin:$JAVA_HOME/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
CMD bash
