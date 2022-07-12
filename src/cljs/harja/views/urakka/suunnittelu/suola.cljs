(ns harja.views.urakka.suunnittelu.suola
  "Urakan suolan käytön suunnittelu"
  (:require [reagent.core :refer [atom wrap]]
            [harja.tiedot.urakka.toteumat.suola :as tiedot-suola]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.napit :refer [palvelinkutsu-nappi]]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.hallinta.indeksit :as i]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.kartta :as kartta]
            [harja.fmt :as fmt]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defn lomake-talvisuolan-kayttoraja
  [urakka]
  (let [saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))]

    (fn []
      (let [tiedot {}
            valittavat-indeksit (map :indeksinimi (i/urakkatyypin-indeksit :hoito))]

        [lomake/lomake {:ei-borderia? true
                        :muokkaa! (fn [uusi]
                                    (log "lomaketta muokattu, tiedot:" (pr-str uusi))
                                    ;; TODO: Tuck event
                                    )}
         [(lomake/rivi
            {:nimi :kopioi-rajoitukset
             :tyyppi :checkbox :palstoja 1
             :teksti "Kopioi rajoitukset tuleville hoitovuosille"})
          (lomake/rivi
            {:nimi :talvisuolaraja
             :tyyppi :positiivinen-numero
             :palstoja 1 :pakollinen? true :muokattava? (constantly saa-muokata?)
             :otsikko "Talvisuolan käyttöraja / vuosi"
             :yksikko "kuivatonnia" :placeholder "Ei rajoitusta"})

          (lomake/rivi
            {:nimi :sanktio :tyyppi :positiivinen-numero
             :palstoja 1 :muokattava? (constantly saa-muokata?)
             :otsikko "Sanktio / ylittävä tonni" :yksikko "€"}

            (when (urakka/indeksi-kaytossa-sakoissa?)
              {:nimi :indeksi :tyyppi :valinta :palstoja 1
               :otsikko "Indeksi"
               :muokattava? (constantly saa-muokata?)
               :valinta-nayta #(if (not saa-muokata?)
                                 ""
                                 (if (nil? %) "Ei indeksiä" (str %)))
               :valinnat (conj valittavat-indeksit nil)}))]
         tiedot]))))

(defn lomake-pohjavesialueen-suolasanktio
  [urakka]
  (let [saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))]

    (fn []
      (let [tiedot {}
            valittavat-indeksit (map :indeksinimi (i/urakkatyypin-indeksit :hoito))]

        [lomake/lomake {:ei-borderia? true
                        :muokkaa! (fn [uusi]
                                    (log "lomaketta muokattu, tiedot:" (pr-str uusi))
                                    ;; TODO: Tuck event
                                    )}
         [(lomake/rivi
            {:nimi :kopioi-rajoitukset
             :tyyppi :checkbox :palstoja 1
             :teksti "Kopioi rajoitukset tuleville hoitovuosille"})
          (lomake/rivi
            {:otsikko "Sanktio / ylittävä tonni"
             :pakollinen? true
             :muokattava? (constantly saa-muokata?) :nimi :vainsakkomaara
             :tyyppi :positiivinen-numero :palstoja 1 :yksikko "€"}

            (when (urakka/indeksi-kaytossa-sakoissa?)
              {:otsikko "Indeksi" :nimi :indeksi :tyyppi :valinta
               :muokattava? (constantly saa-muokata?)
               :valinta-nayta #(if (not saa-muokata?)
                                 ""
                                 (if (nil? %) "Ei indeksiä" (str %)))
               :valinnat (conj valittavat-indeksit nil)

               :palstoja 1}))]
         tiedot]))))



(defn taulukko-rajoitusalueet
  "Rajoitusalueiden taulukko."
  [rajoitusalueet voi-muokata?]
  [grid/grid {:tunniste :id
              :piilota-muokkaus? true
              ;; Estetään dynaamisesti muuttuva "tiivis gridin" tyyli, jotta siniset viivat eivät mene vääriin kohtiin,
              ;; taulukon sarakemääriä muutettaessa. Tyylejä säädetty toteumat.less tiedostossa.
              :esta-tiivis-grid? true
              :tyhja (if (nil? @tiedot-suola/urakan-rajoitusalueet)
                       [yleiset/ajax-loader "Rajoitusalueita haetaan..."]
                       "Ei Rajoitusalueita")}
   [{:otsikko "Tie" :tunniste :tie :hae (comp :tie :tr-osoite) :tasaa :oikea :leveys 0.3}
    {:otsikko "Osoiteväli" :tunniste :tie :hae :tr-osoite
     :fmt (fn [tr-osoite]
            (str
              (str (:aosa tr-osoite) " / " (:aet tr-osoite))
              " – "
              (str (:losa tr-osoite) " / " (:let tr-osoite))))
     :leveys 1}
    {:otsikko "Pituus (m)" :nimi :pituus :fmt fmt/pyorista-ehka-kolmeen :tasaa :oikea :leveys 1}
    {:otsikko "Pituus ajoradat (m)" :nimi :pituus_ajoradat :fmt fmt/pyorista-ehka-kolmeen
     :tasaa :oikea :leveys 1}
    {:otsikko "Pohjavesialue (tunnus)" :nimi :pohjavesialueet
     :luokka "sarake-pohjavesialueet"
     :tyyppi :komponentti
     :komponentti (fn [{:keys [pohjavesialueet]}]
                    (if (seq pohjavesialueet)
                      (into [:div]
                        (mapv (fn [alue]
                                [:div (str (:nimi alue) " (" (:tunnus alue) ")")])
                          pohjavesialueet))
                      "-"))
     :leveys 1}
    {:otsikko "Suolankäyttöraja (t/ajoratakm)" :nimi :suolankayttoraja :tasaa :oikea
     :fmt #(if % (fmt/desimaaliluku % 1) "–") :leveys 1}
    {:otsikko "" :nimi :kaytettava-formaattia? :fmt #(when % "Käytettävä formiaattia") :leveys 1}]
   rajoitusalueet])

;; TODO: Toteuta tuck-event kutsut ja tilanhallinta
(defn virkista-rajoitusalueet-taulukko []
  (let [urakkaid @nav/valittu-urakka-id]
    (go
      (reset! tiedot-suola/urakan-rajoitusalueet nil)
      (reset! tiedot-suola/urakan-rajoitusalueet (<! (tiedot-suola/hae-urakan-rajoitusalueet urakkaid))))))


(defn urakan-suolarajoitukset []
  (komp/luo
    (komp/sisaan
      (fn []
        (virkista-rajoitusalueet-taulukko)))
    (komp/ulos
      (fn []
        ;; TODO: Eroon tila-atomeista. Toteuta tuck-tilanhallinta palanen kerrallaan.
        (reset! tiedot-suola/urakan-rajoitusalueet nil)))

    (fn []
      (let [rajoitusalueet @tiedot-suola/urakan-rajoitusalueet
            urakka @nav/valittu-urakka
            saa-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-suola (:id urakka))]
        [:div.urakan-suolarajoitukset
         [:h2 "Urakan suolarajoitukset hoitovuosittain"]

         [:div.kontrollit
          [valinnat/urakan-hoitokausi urakka]]

         [:div.lomakkeet
          [:h3 "Talvisuolan kokonaismäärän käyttöraja"]
          [lomake-talvisuolan-kayttoraja urakka]

          [:h3 "Pohjavesialueen suolasanktio"]
          [lomake-pohjavesialueen-suolasanktio urakka]]

         ;; Pohjavesialueiden rajoitusalueiden taulukko ym.
         [:div.pohjavesialueiden-suolarajoitukset
          [:div.header
           [:h3 {:class "pull-left"}
            "Pohjavesialueiden suolarajoitukset"]
           [napit/uusi "Lisää rajoitus"
            ;; TODO: Tuck-event
            :D
            {:luokka "pull-right"
             :disabled (not saa-muokata?)}]]

          ;; TODO: Kartta
          #_[kartta]

          [taulukko-rajoitusalueet rajoitusalueet saa-muokata?]]]))))
