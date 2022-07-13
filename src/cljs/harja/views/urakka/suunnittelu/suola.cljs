(ns harja.views.urakka.suunnittelu.suola
  "Urakan suolan käytön suunnittelu"
  (:require [reagent.core :refer [atom wrap]]
            [harja.tiedot.urakka.toteumat.suola :as tiedot-suola]
            [harja.tiedot.urakka :as urakka]
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
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.validointi :as validointi]
            [harja.ui.kentat :as kentat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

;; TODO: Lomakkeen tila tuck app-stateen
(defonce lomake-rajoitusalue-atom (atom {:lomake {}
                                         :auki? false}))
(defn lomake-rajoitusalue-skeema
  [lomake]

  (into []
    (concat
      [(lomake/rivi
         {:nimi :kopioi-rajoitukset :palstoja 3
          :tyyppi :checkbox
          :teksti "Kopioi rajoitukset tuleville hoitovuosille"})]
      [(lomake/ryhma
         {:otsikko "Sijainti"
          :rivi? true}
         {:nimi :tie
          :otsikko "Tie"
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:tie] lomake)
          :virheteksti (validointi/nayta-virhe-teksti [:tie] lomake)}
         {:nimi :aosa
          :otsikko "A-osa"
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:aosa] lomake)}
         {:nimi :aet
          :otsikko "A-et."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:aet] lomake)}
         {:nimi :losa
          :otsikko "L-osa."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:losa] lomake)}
         {:nimi :let
          :otsikko "L-et."
          :tyyppi :positiivinen-numero
          :kokonaisluku? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:let] lomake)})
       (lomake/ryhma
         {:rivi? true}
         {:nimi :pituus
          :otsikko "Pituus (m)"
          :tyyppi :positiivinen-numero
          :vayla-tyyli? true
          :disabled? true
          :tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)}
         {:nimi :pituus-ajoradat
          :otsikko "Pituus ajoradat (m)"
          :tyyppi :positiivinen-numero
          :vayla-tyyli? true
          :disabled? true
          :tarkkaile-ulkopuolisia-muutoksia? true
          :muokattava? (constantly false)})]

      [(lomake/ryhma
         {:otsikko "Suolarajoitus"
          :rivi? true}
         {:nimi :suolarajoitus
          :otsikko "Suolan max määrä"
          :tyyppi :positiivinen-numero
          :yksikkö "t/ajoratakm"
          :vayla-tyyli? true
          :tarkkaile-ulkopuolisia-muutoksia? true})
       (lomake/rivi
         {:nimi :kopioi-rajoitukset
          :tyyppi :checkbox :palstoja 3
          :teksti "Alueella tulee käyttää suolan sijaan formiaattia"})])))

(defn lomake-rajoitusalue
  "Rajoitusalueen lisäys/muokkaus"
  [lomake]
  (let [muokkaustila? true
        ;; TODO: Muokkaus
        ;; TODO: Oikeustarkastukset
        ;; TODO: Tuck-kytkennät
        ]
    [lomake/lomake
     {:ei-borderia? true
      :voi-muokata? true
      :tarkkaile-ulkopuolisia-muutoksia? true
      :otsikko (when muokkaustila?
                 (if (:id lomake) "Muokkaa rajoitusta" "Lisää rajoitusalue"))
      ;; TODO: Muokkaus
      :muokkaa! :D
      :footer-fn (fn [lomake]
                   [:div.row

                    [:div.row
                     [:div.col-xs-8 {:style {:padding-left "0"}}
                      [napit/tallenna
                       "Valmis"
                       ;; TODO: Tuck-event
                       #(swap! lomake-rajoitusalue-atom assoc :auki? false)
                       {:disabled false :paksu? true}]
                      [napit/tallenna
                       "Tallenna ja lisää seuraava"
                       ;; TODO: Tuck-event
                       #(swap! lomake-rajoitusalue-atom assoc :auki? false)
                       {:disabled false :paksu? true}]]
                     [:div.col-xs-4
                      [napit/yleinen-toissijainen
                       "Peruuta"
                       ;; TODO: Tuck-event
                       #(swap! lomake-rajoitusalue-atom assoc :auki? false)
                       {:paksu? true}]]]])}

     (lomake-rajoitusalue-skeema lomake)]))


;; -------

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
            #(swap! lomake-rajoitusalue-atom assoc :auki? true)
            {:luokka "pull-right"
             :disabled (not saa-muokata?)}]]

          ;; TODO: Kartta
          #_[kartta]

          ;; Rajoitusalueen lisäys/muokkauslomake. Avautuu sivupaneeliin
          ;; TODO: Lomakkeen avaaminen/sulkeminen tuck-eventeiksi ja app stateen
          (when (:auki? @lomake-rajoitusalue-atom)
            [:div.overlay-oikealla {:style {:width "600px" :overflow "auto"}}
             [lomake-rajoitusalue
              ;; TODO: Lomake tuck app-statesta
              (:lomake @lomake-rajoitusalue-atom)]])

          [taulukko-rajoitusalueet rajoitusalueet saa-muokata?]]]))))
