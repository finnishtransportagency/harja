(ns harja.views.ilmoituskuittaukset
  "Harjan ilmoituskuittausten listaus & uuden kuittauksen kirjaus lomake."
  (:require [clojure.string :refer [capitalize]]
            [reagent.core :refer [atom]]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoituskuittaukset :as tiedot]
            [harja.domain.ilmoitukset :as apurit]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.bootstrap :as bs]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.ilmoitukset.viestit :as v])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn kasittele-kuittauskasityksen-vastaus [vastaus]
  ;; FIXME: implement
  (comment
    (when vastaus
      (viesti/nayta! "Kuittaus lähetetty Tieliikennekeskukseen." :success)
      (ilmoitukset/lisaa-kuittaus-valitulle-ilmoitukselle vastaus))
    (tiedot/alusta-uusi-kuittaus ilmoitukset/valittu-ilmoitus)
    (ilmoitukset/sulje-uusi-kuittaus!)))

(defn esta-lahetys? [kuittaus]
  (or (:tallennus-kaynnissa? kuittaus)
      (nil? (:tyyppi kuittaus))))

(defn uusi-kuittaus [e! kuittaus]
  [:div
   {:class "uusi-kuittaus"}
   [lomake/lomake
    {:muokkaa! #(e! (v/->AsetaKuittausTiedot %))
     :luokka   :horizontal
     :footer   [:div
                [napit/palvelinkutsu-nappi
                 "Lähetä"
                 #(go (e! (v/->Kuittaa)))
                 {:ikoni        (ikonit/tallenna)
                  :disabled     (esta-lahetys? kuittaus)
                  :virheviesti  "Kuittauksen tallennuksessa tai lähetyksessä T-LOIK:n tapahtui virhe."
                  :luokka       "nappi-ensisijainen"}]
                [napit/peruuta
                 "Peruuta"
                 #(e! (v/->SuljeUusiKuittaus))
                 {:luokka "pull-right"}]]}
    [(lomake/ryhma {:otsikko    "Kuittaus"
                    :leveys-col 3}
                   {:nimi          :tyyppi
                    :otsikko       "Tyyppi"
                    :pakollinen?   true
                    :tyyppi        :valinta
                    :valinnat      apurit/kuittaustyypit
                    :valinta-nayta #(if %
                                      (apurit/kuittaustyypin-selite %)
                                      "- Valitse kuittaustyyppi -")
                    :leveys-col    3}
                   {:nimi        :vapaateksti
                    :otsikko     "Vapaateksti"
                    :tyyppi      :text
                    :leveys-col  3})
     (lomake/ryhma {:otsikko    "Käsittelijä"
                    :leveys-col 3}
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
                    :tyyppi     :puhelin}
                   {:nimi       :kasittelija-tyopuhelin
                    :otsikko    "Työpuhelin"
                    :leveys-col 3
                    :tyyppi     :puhelin}
                   {:nimi       :kasittelija-sahkoposti
                    :otsikko    "Sähköposti"
                    :leveys-col 3
                    :tyyppi     :email}
                   {:nimi       :kasittelija-organisaatio
                    :otsikko    "Organisaation nimi"
                    :leveys-col 3
                    :tyyppi     :string}
                   {:nimi       :kasittelija-ytunnus
                    :otsikko    "Organisaation y-tunnus"
                    :leveys-col 3
                    :tyyppi     :string})]
    kuittaus]])


(defn kuittauksen-tiedot [kuittaus]
  ^{:key (str "kuittaus-paneeli-" (:id kuittaus))}
  [bs/panel
   {:class "kuittaus-viesti"}
   (capitalize (name (:kuittaustyyppi kuittaus)))
   [:span
    ^{:key "kuitattu"}
    [yleiset/tietoja {}
     "Kuitattu: " (pvm/pvm-aika-sek (:kuitattu kuittaus))
     "Lisätiedot: " (:vapaateksti kuittaus)]
    [:br]
    ^{:key "kuittaaja"}
    [yleiset/tietoja {}
     "Kuittaaja: " (apurit/nayta-henkilo (:kuittaaja kuittaus))
     "Puhelinnumero: " (apurit/parsi-puhelinnumero (:kuittaaja kuittaus))
     "Sähköposti: " (get-in kuittaus [:kuittaaja :sahkoposti])]
    [:br]
    (when (:kasittelija kuittaus)
      ^{:key "kasittelija"}
      [yleiset/tietoja {}
       "Käsittelijä: " (apurit/nayta-henkilo (:kasittelija kuittaus))
       "Puhelinnumero: " (apurit/parsi-puhelinnumero (:kasittelija kuittaus))
       "Sähköposti: " (get-in kuittaus [:kasittelija :sahkoposti])])]])

(defn kuittaa-monta-lomake [e! {:keys [ilmoitukset tyyppi vapaateksti] :as data}]
  (let [valittuna (count ilmoitukset)]
    [:div.ilmoitukset-kuittaa-monta
     [lomake/lomake
      {:muokkaa! #(e! (v/->AsetaKuittausTiedot %))
       :palstoja 2
       :otsikko "Kuittaa monta ilmoitusta"}
      [{:otsikko "Kuittaustyyppi"
        :pakollinen? true
        :tyyppi :valinta
        :valinnat apurit/kuittaustyypit
        :valinta-nayta #(or (apurit/kuittaustyypin-selite %) "- Valitse kuittaustyyppi -")
        :nimi :tyyppi}

       {:otsikko "Vapaateksti"
        :tyyppi :text
        :koko [80 :auto]
        :nimi :vapaateksti}]

      data]
     [napit/palvelinkutsu-nappi
      (if (> valittuna 1)
        (str "Kuittaa " valittuna " ilmoitusta")
        "Kuittaa ilmoitus")
      #(go (e! (v/->Kuittaa)))

      {:luokka   "nappi-ensisijainen kuittaa-monta-tallennus"
       :disabled (or (:tallennus-kaynnissa? data)
                     (not (lomake/voi-tallentaa-ja-muokattu? data))
                     (zero? valittuna))}]
     [napit/peruuta "Peruuta" #(e! (v/->PeruMonenKuittaus))]
     [yleiset/vihje "Valitse kuitattavat ilmoitukset listalta."]]))
