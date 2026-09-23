# Ariadne Web

Independent Vue 3 + TypeScript + Vite frontend for the Ariadne fund analysis API. It is not copied into Spring Boot static resources.

## Development

```sh
npm install
npm run dev
```

The Vite development server proxies `/api/*` to `http://127.0.0.1:18080` and removes the `/api` prefix. Override the target without changing source files:

```sh
VITE_JAVA_URL=http://127.0.0.1:18080 npm run dev
```

The browser calls `/api/funds/search?prefix=022` and `/api/funds/{fundCode}/analysis`; search matches fund codes starting with the supplied prefix and returns at most 20 ordered results. If a short prefix returns too many results, continue typing (for example, `022485`) to narrow the list. Vite rewrites these to the Java service routes. `VITE_API_BASE` can change the browser base path for a separately hosted proxy.

## Remote user-unit deployment

The deployed static site is served by the `ecs-user` user unit `ariadne-web.service` at `127.0.0.1:18081`. Its nginx configuration is `/home/ecs-user/ariadne-web/nginx.conf`; the static files are under `/home/ecs-user/ariadne-web/dist`. It proxies `/api/` to the Java service at `127.0.0.1:18080` and does not modify the system nginx instance.

The same isolated nginx instance also listens on `0.0.0.0:5081` for the public reverse proxy URL `http://example.com:5081/`. The host `firewalld` public zone permits only the additional `5081/tcp` port for this site; the cloud security group must separately allow inbound TCP 5081.

```sh
systemctl --user status ariadne-web.service
systemctl --user restart ariadne-web.service
journalctl --user -u ariadne-web.service -f
sudo firewall-cmd --zone=public --list-ports
```

For local browser access through SSH:

```sh
ssh -N -L 18081:127.0.0.1:18081 <ssh-host>
```

Then open `http://127.0.0.1:18081/`.

## Checks

```sh
npm run typecheck
npm test
npm run build
```
