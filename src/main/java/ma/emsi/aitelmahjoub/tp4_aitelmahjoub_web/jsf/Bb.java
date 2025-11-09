package ma.emsi.aitelmahjoub.tp4_aitelmahjoub_web.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.faces.context.FacesContext;
import java.util.ArrayList;
import java.util.List;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import java.io.Serializable;
import ma.emsi.aitelmahjoub.tp4_aitelmahjoub_web.llm.LlmClient;




@Named("bb")
@ViewScoped
public class Bb implements Serializable {

    private String roleSysteme;
    private boolean roleSystemeChangeable = true;
    private String question;
    private String reponse;
    private final StringBuilder conversation = new StringBuilder();

    @Inject
    private LlmClient llm;


    public String envoyer() {
        if (question == null || question.isBlank()) {
            afficherMessage(FacesMessage.SEVERITY_ERROR, "Texte manquant", "Veuillez entrer une question.");
            return null;
        }

        try {
           if (roleSystemeChangeable) {
                llm.setSystemRole(roleSysteme);
                roleSystemeChangeable = false;
            }

            reponse = llm.ask(question);
            enregistrerEchange(question, reponse);

        } catch (Exception e) {
            reponse = null;
            afficherMessage(FacesMessage.SEVERITY_ERROR, "Erreur LLM", e.getMessage());
        }

        return null;
    }


    public String nouveauChat() {
        return "index?faces-redirect=true";
    }


    private void enregistrerEchange(String q, String r) {
        conversation
                .append("== Utilisateur :\n").append(q).append("\n")
                .append("== Assistant :\n").append(r).append("\n\n");
    }

     private void afficherMessage(FacesMessage.Severity type, String resume, String detail) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(type, resume, detail));
    }


    private List<SelectItem> rolesDisponibles;

    public List<SelectItem> getRolesSysteme() {
        if (rolesDisponibles == null) {
            rolesDisponibles = new ArrayList<>();
            String role = "  You are a helpful assistant. You help the user to find the information they need.\n" +
                    "                    If the user type a question, you answer it.\n" +
                    "                  ";
            rolesDisponibles.add(new SelectItem(role, "Assistant"));

            role = "" +
                    " You are an interpreter. You translate from English to French and from French to English.\n" +
                    "                    If the user type a French text, you translate it into English.\n" +
                    "                    If the user type an English text, you translate it into French.\n" +
                    "                    If the text contains only one to three words, give some examples of usage of these words in English.\n" +
                    "                   , add usage examples.";
            rolesDisponibles.add(new SelectItem(role, "Traducteur Anglais–Français"));

            role = "" +
                    "                    Your are a travel guide. If the user type the name of a country or of a town,\n" +
                    "                    you tell them what are the main places to visit in the country or the town\n" +
                    "                    are you tell them the average price of a meal.";
            rolesDisponibles.add(new SelectItem(role, "Guide touristique"));

        }
        return rolesDisponibles;
    }



    public String getRoleSysteme() {
        return roleSysteme;
    }

    public void setRoleSysteme(String roleSysteme) {
        this.roleSysteme = roleSysteme;
    }

    public boolean isRoleSystemeChangeable() {
        return roleSystemeChangeable;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversationTexte) {
        conversation.setLength(0);
        conversation.append(conversationTexte);
    }

    private boolean debug = false;

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String toggleDebug() {
        this.debug = !this.debug;
        return "";
    }
}