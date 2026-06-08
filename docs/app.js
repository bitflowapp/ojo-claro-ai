const RELEASE_PAGE_URL = "https://github.com/bitflowapp/ojo-claro-ai/releases/latest";
const APK_DOWNLOAD_URL = "https://github.com/bitflowapp/ojo-claro-ai/releases/latest/download/estela-ojo-claro-debug.apk";

window.addEventListener("DOMContentLoaded", () => {
  const downloadButton = document.getElementById("download-apk");
  if (downloadButton) {
    downloadButton.href = APK_DOWNLOAD_URL;
    downloadButton.textContent = "Descargar APK de Estela para Android";
  }

  const releaseLink = document.getElementById("release-page");
  if (releaseLink) {
    releaseLink.href = RELEASE_PAGE_URL;
  }
});
