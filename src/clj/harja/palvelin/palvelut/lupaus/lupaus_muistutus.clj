(ns harja.palvelin.palvelut.lupaus.lupaus-muistutus
  "Lähetetään urakoitsijoille muistutusviestejä lupausten kannanotoista"
  (:require [hiccup.core :refer [html]]
            [harja.tyokalut.html :refer [sanitoi]]
            [harja.palvelin.komponentit.fim :as fim-komponentti]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]))

(defn viestin-otsikko
  "Viestin otsikko 2021- alkavalle urakalle."
  [urakan-nimi odottaa-kannanottoa]
  (let [otsikko-muotoilu (str (if (> odottaa-kannanottoa 1)
                                " lupausta odottaa kannanottoa"
                                " lupaus odottaa kannanottoa"))]
    (format "Urakassa '%s' - %s %s" urakan-nimi odottaa-kannanottoa otsikko-muotoilu)))

(defn viestin-otsikko-19-20
  "Viestin otsikko 2019/2020 alkavalle urakalle."
  [urakan-nimi]
  (format "Urakan '%s' lupaukset odottavat kannanottoa." urakan-nimi))

(defn viestin-linkki [hallintayksikko id]
  (format
    "https://extranet.vayla.fi/harja/#urakat/valitavoitteet/lupaukset?&hy=%s&u=%s"
    hallintayksikko
    id))

(defn viestin-sisalto [url urakka-nimi odottaa-kannanottoa]
  (html
    [:div
     [:p (str
           "Urakassa " (sanitoi urakka-nimi)
           " on " (sanitoi odottaa-kannanottoa)
           (if (> odottaa-kannanottoa 1)
             " kannanottoa odottavaa lupausta."
             " kannanottoa odottava lupaus."))]
     [:p (str "Voit käydä täyttämässä lupaukset tästä:")
      [:br]
      url]
     [:p "Tämä on automaattisesti luotu viesti HARJA-järjestelmästä. Älä vastaa tähän viestiin."]]))

(defn viestin-sisalto-19-20 [url urakan-nimi]
  (html
    [:div
     [:p (format "Urakan '%s' lupaukset odottavat kannanottoa." (sanitoi urakan-nimi))]
     [:p (str "Voit käydä täyttämässä lupaukset tästä:")
      [:br]
      url]
     [:p "Tämä on automaattisesti luotu viesti HARJA-järjestelmästä. Älä vastaa tähän viestiin."]]))

(defn viesti-urakalle
  "Lupausmuistutusviesti 2021- alkavalle urakalle."
  [id hallintayksikko nimi odottaa-kannanottoa]
  (let [otsikko (viestin-otsikko nimi odottaa-kannanottoa)
        sisalto (viestin-sisalto
                  (viestin-linkki hallintayksikko id)
                  nimi
                  odottaa-kannanottoa)]
    {:otsikko otsikko
     :sisalto sisalto}))

(defn viesti-urakalle-19-20
  "Lupausmuistutusviesti 2019/2020 alkavalle urakalle."
  [id hallintayksikko nimi]
  (let [otsikko (viestin-otsikko-19-20 nimi)
        sisalto (viestin-sisalto-19-20
                  (viestin-linkki hallintayksikko id)
                  nimi)]
    {:otsikko otsikko
     :sisalto sisalto}))

(defn urakan-vastaanottajat [fim sampoid]
  (when fim
    (->>
      (fim-komponentti/hae-urakan-kayttajat-jotka-roolissa fim sampoid #{"urakan vastuuhenkilö"})
      (mapv :sahkoposti))))

(defn laheta-viesti-vastaanottajalle [email vastaanottaja {:keys [otsikko sisalto] :as viesti}]
  (let [vastaanottaja-str (pr-str ((juxt :sahkoposti :etunimi :sukunimi) vastaanottaja))]
    (log/debug "Lähetetään sähköposti vastaanottajalle: " vastaanottaja-str)
    (sahkoposti/laheta-viesti!
      email
      (sahkoposti/vastausosoite email)
      vastaanottaja
      otsikko
      sisalto)
    (log/debug "Sähköposti lähetetty vastaanottajalle: " vastaanottaja-str)))

(defn laheta-viesti-vastaanottajille
  [email vastaanottajat viesti]
  (doseq [vastaanottaja vastaanottajat]
    (laheta-viesti-vastaanottajalle email vastaanottaja viesti)))

(defn alkuvuosi-19-20? [alkupvm]
  (#(2019 2020) (pvm/vuosi alkupvm)))

(defn laheta-muistutus-urakalle
  [fim email {:keys [id sampoid hallintayksikko nimi]} odottaa-kannanottoa]
  (log/info (format "Lähetetään muistutus urakan lupauksista urakalle %s (id: %s)" nimi id))
  (let [vastaanottajat (urakan-vastaanottajat fim sampoid)
        viesti (viesti-urakalle id hallintayksikko nimi odottaa-kannanottoa)]
    (laheta-viesti-vastaanottajille email vastaanottajat viesti)))
