const RELEASE_PAGE_URL = "https://github.com/bitflowapp/ojo-claro-ai/releases/latest";

window.addEventListener("DOMContentLoaded", () => {
  const downloadButton = document.getElementById("download-apk");
  if (downloadButton) {
    downloadButton.href = RELEASE_PAGE_URL;
    downloadButton.textContent = "Descargar alpha para Android";
  }

  const releaseLink = document.getElementById("release-page");
  if (releaseLink) {
    releaseLink.href = RELEASE_PAGE_URL;
  }
});
