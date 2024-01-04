(ns harja.views.hallinta.viestitestaus-nakyma
  "Työkalu toteumien lisäämiseksi testiurakoille."
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.tiedot.hallinta.viestitestaus-tiedot :as tiedot]))

(defn viestitestaus* [e! {:keys [email emailapi tekstiviesti] :as app}]
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
                             false)
        disable-laheta-sms? (if (or
                                  (nil? (:puhelinnumero tekstiviesti))
                                  (nil? (:viesti tekstiviesti)))
                              true
                              false)]
    [:div
     [:h1 "Sähköpostin ja tekstiviestin lähetyksen testaustoiminnot"]
     [:p "Voit testata täällä eri ympäristöissä sähköpostin ja tekstiviestin lähettämistä."]
     [:p " Sähköpostin lähettämiseen on kaksi formaattia. Viestit voidaan lähettää suoraan 'postal' nimisellä kirjastolla, jolloin tarvitset esim gmailia varten
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
      emailapi]
     [:h2 "Lähetä tekstiviesti LinkMobilityn LinkSMS-palvelulla."]
     [:p "LinkSMS-palveusta on Harjassa usein käytetty vanhaa nimeä Labyrintti. Jos testaat kehitysympäristössä, katso ReadMe-tiedostosta mitä muutoksia pitää tehdä main.clj-tiedostoon ja millainen ssh-yhteys tarvitaan." ]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [email]
                    [:div
                     [napit/tallenna "Lähetä SMS"
                      #(e! (tiedot/->LahetaSMS tekstiviesti))
                      {:disabled disable-laheta-sms? :paksu? true}]])
       :muokkaa! #(e! (tiedot/->MuokkaaSMS %))}
      [{:nimi :puhelinnumero
        :otsikko "Puhelinnumero"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :viesti
        :otsikko "Viesti"
        :tyyppi :string
        :pakollinen? true}]
      tekstiviesti]
     ]))

(defn viestitestaus []
  [tuck tiedot/tila viestitestaus*])
