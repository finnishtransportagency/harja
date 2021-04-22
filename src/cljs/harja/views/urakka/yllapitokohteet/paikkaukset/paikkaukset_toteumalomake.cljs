(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake
  (:require [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as t-toteumalomake]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]))

(defn nayta-virhe? [polku toteumalomake]
  (let [validi? (if (nil? (get-in toteumalomake polku))
                  true ;; kokeillaan palauttaa true, jos se on vaan tyhjä. Eli ei näytetä virhettä tyhjälle kentälle
                  (get-in toteumalomake [::tila/validius polku :validi?]))]
    ;; Koska me pohjimmiltaan tarkistetaan, validiutta, mutta palautetaan tieto, että näytetäänkö virhe, niin käännetään
    ;; boolean ympäri
    (not validi?)))

(defn- maarakentan-otsikko
  "Määrä -kentän otsikko riippuu valitusta yksiköstä"
  [yksikko]
  (cond
    (= "kpl" yksikko) "Kappalemäärä"
    (= "m2" yksikko) "Pinta-ala"
    (= "jm" yksikko) "Juoksumetriä"
    (= "t" yksikko) "Tonnia"
    :else "Tonnia"))

(defn paivamaara-kentat [toteumalomake]
  [(lomake/ryhma
     {:otsikko ""
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}
     {:otsikko "Työ alkoi"
      :tyyppi :pvm
      :nimi :alkuaika
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:alkuaika] toteumalomake)
      ::lomake/col-luokka "col-sm-6"}
     {:otsikko "Työ päättyi"
      :tyyppi :pvm
      :nimi :loppuaika
      :pakollinen? true
      :vayla-tyyli? true
      :pvm-tyhjana #(:alkuaika %)
      :rivi toteumalomake
      :virhe? (nayta-virhe? [:loppuaika] toteumalomake)
      ::lomake/col-luokka "col-sm-6"})])

(defn maara-kentat [toteumalomake]
  [(lomake/ryhma
     {:otsikko "Määrä"
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}
     {:otsikko (maarakentan-otsikko (:kohteen-yksikko toteumalomake))
      :tyyppi :positiivinen-numero
      :nimi :maara
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:maara] toteumalomake)
      ::lomake/col-luokka "col-sm-6"})])

(defn sijainnin-kentat [toteumalomake]
  [(lomake/ryhma
     {:otsikko "Sijainti"
      :rivi? true
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}

     {:otsikko "Tie"
      :tyyppi :numero
      :nimi :tie
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:tie] toteumalomake)
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "Ajorata"
      :tyyppi :valinta
      :valinnat {nil "Ei ajorataa"
                 0 0
                 1 1
                 2 2} ; TODO: Hae tietokannasta
      :valinta-arvo first
      :valinta-nayta second
      :nimi :ajorata
      :vayla-tyyli? true
      :pakollinen? false})
   (lomake/rivi
     {:otsikko "A-osa"
      :tyyppi :numero
      :pakollinen? true
      :nimi :aosa
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:aosa] toteumalomake)
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "A-et."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:aet] toteumalomake)
      :nimi :aet}
     {:otsikko "L-osa."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:losa] toteumalomake)
      :nimi :losa}
     {:otsikko "L-et."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:let] toteumalomake)
      :nimi :let}
     {:otsikko "Pituus (m)"
      :tyyppi :numero
      :vayla-tyyli? true
      :disabled? true
      :nimi :pituus
      :tarkkaile-ulkopuolisia-muutoksia? true
      :muokattava? (constantly false)})])

(defn  toteuma-skeema
  "Määritellään toteumalomakkeen skeema. Skeema on jaoteltu osiin, jotta saadaan ulkonäöllisesti harmaat laatikot
  rakennettua eri input elementtien ympärille."
  [voi-muokata? toteumalomake]
  (let [pvmkentat (when voi-muokata?
                         (paivamaara-kentat toteumalomake))
        sijainti (when voi-muokata?
                   (sijainnin-kentat toteumalomake))
        maara (when voi-muokata?
                      (maara-kentat toteumalomake))]
    (vec (concat pvmkentat
                 sijainti
                 maara))))

(defn- footer-vasemmat-napit [e! toteumalomake muokkaustila? voi-tilata? voi-perua?]
  (let [voi-tallentaa? (::tila/validi? toteumalomake)]
    [:div
     ;; Lomake on auki
     (when muokkaustila?
       [:div
        [napit/tallenna
         "Tallenna muutokset"
         #(e! (t-toteumalomake/->TallennaToteuma (lomake/ilman-lomaketietoja toteumalomake)))
         {:disabled (not voi-tallentaa?) :paksu? true}]
        ;; Toteuman on pakko olla tietokannassa, ennenkuin sen voi poistaa
        (when (:id toteumalomake)
          [napit/yleinen-toissijainen
           "Poista toteuma"
           (t-paikkauskohteet/nayta-modal
             (str "Poistetaanko toteuma \"" (:nimi toteumalomake) "\"?")
             "Toimintoa ei voi perua."
             [napit/yleinen-toissijainen "Poista toteuma" #(e! (t-toteumalomake/->PoistaToteuma
                                                                 (lomake/ilman-lomaketietoja toteumalomake))) {:paksu? true}]
             [napit/yleinen-toissijainen "Säilytä toteuma" modal/piilota! {:paksu? true}])
           {:ikoni (ikonit/livicon-trash) :paksu? true}])])
     ]))

(defn toteumalomake [e! toteumalomake]
  (let [muokkaustila? (or
                        (= :toteuman-muokkaus (:tyyppi toteumalomake))
                        (= :uusi-toteuma (:tyyppi toteumalomake)))]
    
    ;; Wrapataan lomake vain diviin. Koska tämä aukaistaan eri kokoisiin oikealta avattaviin diveihin eri näkymistä
    [:div
     [lomake/lomake
      {:ei-borderia? true
       :voi-muokata? muokkaustila?
       :otsikko "Muutama tämä, kun tiedossa"
       :muokkaa! #(e! (t-toteumalomake/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
       :footer-fn (fn [toteumalomake]
                    [:div.row
                     ;; UI on jaettu kahteen osioon. Oikeaan ja vasempaan.
                     ;; Tarkistetaan ensin, että mitkä näapit tulevat vasemmalle
                     [:div.row
                      [:div.col-xs-8 {:style {:padding-left "0"}}
                       [footer-vasemmat-napit e! toteumalomake muokkaustila?]]
                      [:div.col-xs-4
                       [:div {:style {:text-align "end"}}
                        ;; Lomake on auki
                        (when muokkaustila?
                          [napit/yleinen-toissijainen
                           "Peruuta"
                           #(e! (t-toteumalomake/->SuljeToteumaLomake))
                           {:paksu? true}])]]]])}
      (toteuma-skeema muokkaustila? toteumalomake)
      toteumalomake]]))
