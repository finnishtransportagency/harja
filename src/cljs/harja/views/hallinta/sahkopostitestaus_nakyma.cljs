(ns harja.views.hallinta.sahkopostitestaus-nakyma
  "Työkalu toteumien lisäämiseksi testiurakoille."
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.hallinta.sahkopostitestaus-tiedot :as tiedot])
  (:require-macros [cljs.core.async.macros :refer [go]]))



(defn email-testaus* [e! {:keys [email emailapi] :as app}]
  (let [disable-laheta? (if (or (nil? (:palvelin email))
                              (nil? (:tunnus email))
                              (nil? (:salasana email))
                              (nil? (:portti email))
                              (nil? (:viesti email))
                              (nil? (:otsikko email))
                              (nil? (:vastaanottaja email))
                              (nil? (:lahettaja email)))
                          true
                          false)
        disable-lahetaapi? (if (or
                                 (nil? (:viesti emailapi))
                                 (nil? (:otsikko emailapi))
                                 (nil? (:vastaanottaja emailapi))
                                 (nil? (:lahettaja emailapi)))
                             true
                             false)]
    [:div [:p "Voit testata täällä eri ympäristöissä sähköpostien lähettämistä. Sähköpostin lähettämiseen on
  kaksi formaattia. Viestit voidaan lähettää suoraan 'postal' nimisellä kirjastolla, jolloin tarvitset esim gmailia varten
  gmailin tarvitsemat asetukset ja tunnukset. Toinen vaihtoehto on lähettää viestit Digian tarjoamaa api-rajapintaa hyödyntäen.
  Jälkimmäinen tapa simuloi paremmin sitä, mitä Harja tekee, kun se lähettää sähköposteja. Ensimmäinen on lähinnä
  lokaaliin ympäristöön testitarkoituksissa."]
     [:p "Viestin lähetykseen voit käyttää vaikka Gmailia. Vaihdat asetukset.edn tiedostoon :ulkoinen-sahkoposti :palvelin arvon 'stmp.gmail.com'."]
     [:p "Jos Gmailissa on käytössä 2FA, niin luo sähköpostin lähettämistä varten erillinen sovelluskohtainen salasana Google Tilillä: 'https://myaccount.google.com/security?hl=fi'."]
     [:p "Tunnus ja lähettäjä on Gmailissa sinun oma sähköpostiosoite. Porttina 587 ja tsl = true. Lähettävä palvelin on stmp.gmail.com."]
     [:h2 "Lähetä 'postal' kirjastolla"]
     [debug/debug app]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [email]
                    [:div
                     [napit/tallenna "Lähetä"
                      #(e! (tiedot/->Laheta email))
                      {:disabled disable-laheta? :paksu? true}]])
       :muokkaa! #(e! (tiedot/->Muokkaa %))}
      [{:nimi :palvelin
        :otsikko "Lähettävä palvelin"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :tunnus
        :otsikko "Tunnus"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :salasana
        :otsikko "Salasana"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :portti
        :otsikko "Portti"
        :tyyppi :numero
        :pakollinen? true}
       {:nimi :tls
        :otsikko "Käytä tsl?"
        :tyyppi :valinta
        :valinnat [true false]
        :valinta-nayta #(case %
                          true "Kyllä"
                          false "Ei"
                          "Valitse kyllä/ei")
        :pakollinen? true}
       {:nimi :otsikko
        :otsikko "Otsikko"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :viesti
        :otsikko "Viesti"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :vastaanottaja
        :otsikko "Vastaanottaja"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :lahettaja
        :otsikko "Lähettäjä"
        :tyyppi :string
        :pakollinen? true}]
      email]
     [:h2 "Lähetä Digian API:lla sähköpostia."]
     [:p "Tämä simuloi paremmin sitä, mitä Harja tekee, kun sähköpostia lähetetään."]
     [:p "Jos haluat lähettää esim tienpäällystyksen valmistumisesta viestin, niin joudut tekemään muutoksia
     backendiin."]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [email]
                    [:div
                     [napit/tallenna "LähetäAPIlla"
                      #(e! (tiedot/->LahetaAPI email))
                      {:disabled disable-lahetaapi? :paksu? true}]])
       :muokkaa! #(e! (tiedot/->MuokkaaAPI %))}
      [{:nimi :otsikko
        :otsikko "Otsikko"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :viesti
        :otsikko "Viesti"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :vastaanottaja
        :otsikko "Vastaanottaja"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :lahettaja
        :otsikko "Lähettäjä"
        :tyyppi :string
        :pakollinen? true}]
      emailapi]]))

(defn email-testaus []
  [tuck tiedot/tila email-testaus*])
