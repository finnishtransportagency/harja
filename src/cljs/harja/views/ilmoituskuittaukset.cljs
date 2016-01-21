(ns harja.views.ilmoituskuittaukset
  "Harjan ilmoituskuittausten listaus & uuden kuittauksen kirjaus lomake."
  (:require [clojure.string :refer [capitalize]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoituskuittaukset :as tiedot]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.bootstrap :as bs]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]))

(defn tallenna-kuittaus [kuittaus]
  (log "Koitetaan tallentaa uusi kuittaus!")
  (log (pr-str kuittaus))
  (tiedot/tallenna-uusi-kuittaus kuittaus)
  ;; todo: tallentamisen jälkeen pitäisi jotenkin päivittää kuittaukset
  )

(defn luo-lomake []
  [:span
   [napit/takaisin "Palaa ilmoitukseen" #(reset! tiedot/uusi-kuittaus nil)]
   [lomake/lomake
    {:muokkaa! #(reset! tiedot/uusi-kuittaus %)
     :luokka   :horizontal
     :footer   [napit/palvelinkutsu-nappi
                "Tallenna"
                #(tallenna-kuittaus @tiedot/uusi-kuittaus)
                {:ikoni        (ikonit/tallenna)
                 :disabled     false
                 :kun-onnistuu (fn [_] (reset! tiedot/uusi-kuittaus nil))}]}
    [{:nimi          :tyyppi
      :otsikko       "Tyyppi"
      :pakollinen?   true
      :tyyppi        :valinta
      :valinnat      [:vastaanotettu :aloitettu :lopetettu :muutos]
      :valinta-nayta #(case %
                       :vastaanotettu "Vastaanotettu"
                       :aloitettu "Aloitettu"
                       :lopetettu "Lopetettu"
                       :muutos "Muutos"
                       "- valitse -")
      :leveys-col    4}
     {:nimi        :vapaateksti
      :otsikko     "Vapaateksti"
      :pakollinen? false
      :tyyppi      :string
      :pituus-max  256
      :leveys-col  4}
     (lomake/ryhma {:otsikko    "Käsittelijä"
                    :leveys-col 5}
                   {:nimi       :kasittelija-etunimi
                    :otsikko    "Etunimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-sukunimi
                    :otsikko    "Sukunimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-matkapuhelin
                    :otsikko    "Matkapuhelin"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-tyopuhelin
                    :otsikko    "Työpuhelin"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-sahkoposti
                    :otsikko    "Sähköposti"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-organisaatio
                    :otsikko    "Organisaation nimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-ytunnus
                    :otsikko    "Organisaation y-tunnus"
                    :leveys-col 3
                    :tyyppi     :string}
                   )
     (lomake/ryhma {:otsikko    "Ilmoittaja"
                    :leveys-col 5}
                   {:nimi       :ilmoittaja-etunimi
                    :otsikko    "Etunimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :ilmoittaja-sukunimi
                    :otsikko    "Sukunimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :ilmoittaja-matkapuhelin
                    :otsikko    "Matkapuhelin"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :ilmoittaja-tyopuhelin
                    :otsikko    "Työpuhelin"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :ilmoittaja-sahkoposti
                    :otsikko    "Sähköposti"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :ilmoittaja-organisaatio
                    :otsikko    "Organisaation nimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :ilmoittaja-ytunnus
                    :otsikko    "Organisaation y-tunnus"
                    :leveys-col 3
                    :tyyppi     :string})]
    @tiedot/uusi-kuittaus]])

(defn uusi-kuittaus-lomake []
  (komp/luo
    (komp/sisaan-ulos
      #(do
        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
        (nav/vaihda-kartan-koko! :hidden))
      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))

    (fn []
      (luo-lomake))))

(defn kuittauksen-tiedot
  [kuittaus]
  (with-meta
    [bs/panel
     {:class "kuittaus-viesti"}
     (capitalize (name (:kuittaustyyppi kuittaus)))
     [:span
      [yleiset/tietoja {}
       "Kuitattu: " (pvm/pvm-aika-sek (:kuitattu kuittaus))
       "Lisätiedot: " (:vapaateksti kuittaus)]
      [:br]
      [yleiset/tietoja {}
       "Kuittaaja: " (apurit/nayta-henkilo (:kuittaaja kuittaus))
       "Puhelinnumero: " (apurit/parsi-puhelinnumero (:kuittaaja kuittaus))
       "Sähköposti: " (get-in kuittaus [:kuittaaja :sahkoposti])]]]
    {:key (:id kuittaus)}))

