async function copyToClipboard(idTextArea) {
    var textArea = document.getElementById("form:" + idTextArea);
    if(textArea) {
        try {
            await navigator.clipboard.writeText(textArea.value);
        } catch (err) {
            console.error("Erreur lors de la copie : ", err)
        }
    }

}

function toutEffacer() {
    document.getElementById("form:question").value = "";
    document.getElementById("form:reponse").value = "";
}