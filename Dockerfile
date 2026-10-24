FROM nginx:1.27-alpine

# Ativa ${NGINX_LOCAL_RESOLVERS} (DNS do contêiner) no template do nginx.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

# Player das animações Lottie da cena.
RUN mkdir -p /usr/share/nginx/html/vendor \
 && wget -qO /usr/share/nginx/html/vendor/lottie.min.js https://cdnjs.cloudflare.com/ajax/libs/lottie-web/5.12.2/lottie.min.js

COPY docker/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY web/ /usr/share/nginx/html/
