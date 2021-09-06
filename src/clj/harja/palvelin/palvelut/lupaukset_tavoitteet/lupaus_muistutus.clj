(ns harja.palvelin.palvelut.lupaukset-tavoitteet.lupaus-muistutus
  "Lähetetään urakoitsijoille muistutusviestejä lupausten kannanotoista"
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.html :as html-tyokalut]
            [harja.palvelin.komponentit.fim :as fim-komponentti]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]))

(defn- muotoile-viesti [url urakka-nimi odottaa-kannanottoa]
  (let [lupaukset-muotoilu (str (if (> odottaa-kannanottoa 1)
                                  " kannanottoa odottavaa lupausta."
                                  " kannanottoa odottava lupaus."))]
    (html
      [:div
       [:p (str "Urakassa " urakka-nimi " on " (html-tyokalut/sanitoi odottaa-kannanottoa) lupaukset-muotoilu)]
       [:p (str "Voit käydä täyttämässä lupaukset tästä:")
        [:br]
        url]
       [:p "Tämä on automaattisesti luotu viesti HARJA-järjestelmästä. Älä vastaa tähän viestiin."]])))

(defn laheta-muistutus-urakalle [fim email {:keys [id sampoid hallintayksikko nimi]} odottaa-kannanottoa]
  (log/info (format "Lähetetään muistutus urakan lupauksista urakalle %s (id: %s)" nimi id))
  (let [url (format "https://extranet.vayla.fi/harja/#urakat/valitavoitteet/lupaukset?&hy=%s&u=%s"
                    hallintayksikko
                    id)
        otsikko-muotoilu (str (if (> odottaa-kannanottoa 1)
                                " lupausta odottaa kannanottoa"
                                " lupaus odottaa kannanottoa"))
        otsikko (format "Urakassa '%s' - %s %s" nimi odottaa-kannanottoa otsikko-muotoilu)
        sisalto (muotoile-viesti url (html-tyokalut/sanitoi nimi) odottaa-kannanottoa)
        vastaanottajat (when fim (fim-komponentti/hae-urakan-kayttajat-jotka-roolissa fim sampoid #{"urakan vastuuhenkilö"}))]
    (when-not (empty? vastaanottajat)
      (doall
        (doseq [vastaanottaja vastaanottajat]
          (do
            (sahkoposti/laheta-viesti!
              email
              (sahkoposti/vastausosoite email)
              (:sahkoposti vastaanottaja)
              otsikko
              sisalto)
            (log/debug "Sähköpostilähetetty vastaanottajalle:" (pr-str ((juxt :sahkoposti :etunimi :sukunimi) vastaanottaja)))))))))
