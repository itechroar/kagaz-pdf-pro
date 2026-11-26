import { DOWNLOAD_URLS } from "@editor/constants/downloads";

export type DockerImageTag = "latest" | "latest-fat" | "latest-ultra-lite";

export const DOCKER_IMAGE = "ghcr.io/itechroar/kagaz-pdf";

/** `env` adds settings the server starts with, such as a licence key. */
export const dockerRunCommand = (
  tag: DockerImageTag = "latest",
  env: Record<string, string> = {},
) =>
  [
    "docker run -d --name kagaz-pdf -p 8080:8080 \\",
    "  -v ./kagaz-data:/configs \\",
    ...Object.entries(env).map(([name, value]) => `  -e ${name}=${value} \\`),
    `  ${DOCKER_IMAGE}:${tag}`,
  ].join("\n");

export const DOCKER_COMPOSE_FILE = `# Save as docker-compose.yml, then run: docker compose up -d
services:
  kagaz-pdf:
    image: ${DOCKER_IMAGE}:latest
    ports:
      - "8080:8080"
    volumes:
      - ./kagaz-data:/configs
    restart: unless-stopped`;

export const HELM_INSTALL_COMMAND = `helm repo add kagaz-pdf https://itechroar.github.io/kagaz-pdf-chart
helm repo update
helm install kagaz-pdf kagaz-pdf/kagaz-pdf-chart \\\n  --namespace kagaz-pdf --create-namespace`;

export const SERVER_JAR_URL =
  "https://github.com/itechroar/kagaz-pdf/Kagaz-PDF-with-login.jar";

export const JAR_RUN_COMMAND =
  "java -Xmx2g -jar Kagaz-PDF-with-login.jar\n# then open http://localhost:8080";

export const SELF_HOST_GUIDES = {
  docker: "https://github.com/itechroar/kagaz-pdf/Installation/Docker%20Install/",
  kubernetes: "https://github.com/itechroar/kagaz-pdf/Installation/Kubernetes%20Install/",
  manual: DOWNLOAD_URLS.LINUX_DOCS,
} as const;
