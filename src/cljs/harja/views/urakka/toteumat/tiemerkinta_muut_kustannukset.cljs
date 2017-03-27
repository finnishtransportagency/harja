(ns harja.views.urakka.toteumat.tiemerkinta-muut-kustannukset
  (:require [reagent.core :refer [atom] :as r]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki
                                                  livi-pudotusvalikko +korostuksen-kesto+ kuvaus-ja-avainarvopareja]]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.toteumat.tiemerkinta-muut-kustannukset :as tiedot]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-xf]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction-writable]]))

(defn voi-kirjoittaa? [urakka-id]
  (oikeudet/voi-kirjoittaa?
    oikeudet/urakat-toteutus-muutkustannukset
    urakka-id))

(defn valinta-muun-kustannuksen-tyyppi
  [kustannustyypit valittu-kustannustyyppi-atom valitse-fn]
  [:div.label-ja-alasveto.muukustannustyyppi
   [:span.alasvedon-otsikko "Tyyppi"]
   [livi-pudotusvalikko {:valinta @valittu-kustannustyyppi-atom
                         :format-fn #(if % (str/capitalize (name %)) "Kaikki")
                         :valitse-fn valitse-fn}
    (cons nil kustannustyypit)]])

(defn- valinnat [e! {:keys [valittu-urakka valittu-sopimusnumero
                            valitse-sopimusnumero valitun-urakan-hoitokaudet
                            valittu-hoitokausi valitse-hoitokausi
                            valittu-kustannustyyppi valitse-kustannustyyppi
                            kustannustyypit]}]
  (let [muokkausoikeus? (voi-kirjoittaa? (:id valittu-urakka))]
    [:span
     (valinnat/urakan-sopimus-ja-hoitokausi
       valittu-urakka
       valittu-sopimusnumero valitse-sopimusnumero
       valitun-urakan-hoitokaudet valittu-hoitokausi valitse-hoitokausi)
     [valinta-muun-kustannuksen-tyyppi kustannustyypit valittu-kustannustyyppi valitse-kustannustyyppi]
     [napit/uusi "Lisää toteuma" #(e! (tiedot/->UusiToteuma))
      {:disabled (not muokkausoikeus?)}]]))

(defn muu-tyo-lomake [e! tila {:keys [valittu-urakka valittu-sopimusnumero] :as riippuvuudet}]
  (let [vanha-toteuma? (get-in tila [:valittu-toteuma :id])
        valinnat (:valinnat tila)
        muokkausoikeus? (voi-kirjoittaa? (:id valittu-urakka))
        tallenna (fn [{:keys [toteuma urakka-id sopimusnumero
                              sopimuskauden-alkupvm sopimuskauden-loppupvm
                              uusi-laskentakohde]} laskentakohteet]
                   (tiedot/tallenna-toteuma {:toteuma toteuma
                                             :urakka urakka-id
                                             :sopimus sopimusnumero
                                             :alkupvm sopimuskauden-alkupvm
                                             :loppupvm sopimuskauden-loppupvm
                                             :uusi-laskentakohde uusi-laskentakohde}
                                            laskentakohteet))]
    (log "TILA:" (pr-str tila))
    [:div
     [napit/takaisin "Takaisin toteumaluetteloon"
      #(e! (tiedot/->ValitseToteuma nil))]
     [lomake {:otsikko (if vanha-toteuma?
                         "Muokkaa toteumaa"
                         "Luo uusi toteuma")
              :luokka :horizontal
              :voi-muokata? muokkausoikeus?
              :muokkaa! #(e! (tiedot/->MuokkaaToteumaa %))
              :footer [:span
                       [napit/palvelinkutsu-nappi
                        "Tallenna toteuma"
                        #(tallenna {:toteuma (:valittu-toteuma tila)
                                    :urakka-id (:id valittu-urakka)
                                    :sopimusnumero (first valittu-sopimusnumero)
                                    :sopimuskauden-alkupvm (first (:sopimuskausi valinnat))
                                    :sopimuskauden-loppupvm (second (:sopimuskausi valinnat))
                                    :uusi-laskentakohde (:uusi-laskentakohde tila)}
                                   (:laskentakohteet tila))
                        {:luokka "nappi-ensisijainen"
                         :ikoni (ikonit/tallenna)
                         :kun-onnistuu #(e! (tiedot/->ToteumaTallennettu %))
                         :disabled (or (not (lomake/voi-tallentaa? (:valittu-toteuma tila)))
                                       (not muokkausoikeus?))}]
                       [napit/palvelinkutsu-nappi
                        "Poista toteuma"
                        #(tallenna {:toteuma (assoc (:valittu-toteuma tila) :poistettu true)
                                    :urakka-id (:id valittu-urakka)
                                    :sopimusnumero (first valittu-sopimusnumero)
                                    :sopimuskauden-alkupvm (first (:sopimuskausi valinnat))
                                    :sopimuskauden-loppupvm (second (:sopimuskausi valinnat))
                                    :uusi-laskentakohde (:uusi-laskentakohde tila)}
                                   (:laskentakohteet tila))
                        {:luokka "nappi-kielteinen pull-right"
                         :ikoni (ikonit/livicon-trash)
                         :kun-onnistuu #(e! (tiedot/->ToteumaTallennettu %))
                         :disabled (or (not (lomake/voi-tallentaa? (:valittu-toteuma tila)))
                                       (not muokkausoikeus?))}]]}

      [{:otsikko "Päivämäärä" :nimi :pvm :tyyppi :pvm :pakollinen? true}
       {:otsikko "Tyyppi" :nimi :tyyppi
        :pakollinen? true
        :tyyppi :valinta
        :valinta-nayta #(if (nil? %) "- Valitse tyyppi -" (str/capitalize (name %)))
        :valinnat tiedot/+kustannustyypit+
        :fmt #(str/capitalize (name %))
        :validoi [[:ei-tyhja "Anna kustannustyyppi"]]}
       {:otsikko "Hinta" :nimi :hinta :tyyppi :positiivinen-numero :pakollinen? true}
       {:otsikko "Ylläpitoluokka" :nimi :yllapitoluokka :tyyppi :valinta
        :valinta-nayta #(if % (:nimi %) "- valitse -")
        :fmt :nimi
        :valinnat yllapitokohteet-domain/nykyiset-yllapitoluokat}

       {:otsikko "Laskentakohde"
        :nimi :laskentakohde
        :placeholder "Hae kohde tai luo uusi"
        :tyyppi :haku
        :kun-muuttuu #(e! (tiedot/->LaskentakohdeMuuttui %))
        :hae-kun-yli-n-merkkia 0
        :nayta second
        :lahde tiedot/laskentakohdehaku
        :sort-fn #(let [termi (str/lower-case (second %))]
                    (if (nil? (first %))
                      "000"                                 ;; verrattava samaa tyyppiä, siksi nil castattu stringiksi joka sorttautuu ensimmäiseksi
                      termi))}
       {:otsikko "Selite" :nimi :selite :tyyppi :text :pakollinen? true}]
      (:valittu-toteuma tila)]]))


(defn- yhteenveto [toteumat valittu-kustannustyyppi]
  (let [toteumat-yhteensa (->> toteumat (map :hinta) (reduce +))]
    [yleiset/taulukkotietonakyma {}
     (str "Toteumat"
          (when-not (nil? valittu-kustannustyyppi)
            (str " tyyppiä " (name valittu-kustannustyyppi))) " yhteensä: ")
     (fmt/euro-opt toteumat-yhteensa)]))

(defn- muut-tyot-lista [e!
                        {:keys [toteumat] :as tila}
                        {:keys [valittu-urakka valittu-sopimusnumero
                                valitse-sopimusnumero valitun-urakan-hoitokaudet
                                valittu-hoitokausi valitse-hoitokausi
                                valittu-kustannustyyppi valitse-kustannustyyppi
                                kustannustyypit]
                         :as riippuvuudet}]
  (let [valitut-toteumat (filter #(if-not (nil? @valittu-kustannustyyppi)
                                    (= @valittu-kustannustyyppi (:tyyppi %))
                                    identity)
                                 toteumat)]
    [:div
     [valinnat e! {:valittu-urakka valittu-urakka
                   :valittu-sopimusnumero valittu-sopimusnumero
                   :valitse-sopimusnumero valitse-sopimusnumero
                   :valitun-urakan-hoitokaudet valitun-urakan-hoitokaudet
                   :valittu-hoitokausi valittu-hoitokausi
                   :valitse-hoitokausi valitse-hoitokausi
                   :valittu-kustannustyyppi valittu-kustannustyyppi
                   :valitse-kustannustyyppi valitse-kustannustyyppi
                   :kustannustyypit kustannustyypit}]
     [grid/grid
      {:otsikko (str "Muut työt")
       :tyhja (if (nil? toteumat)
                [ajax-loader "Toteumia haetaan..."]
                "Ei toteumia.")
       :rivi-klikattu #(e! (tiedot/->HaeToteuma {:id (:id %)
                                                 :urakka (:id valittu-urakka)}))}
      [{:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm-opt :nimi :pvm :leveys 10}
       {:otsikko "Tyyppi" :tyyppi :string :nimi :tyyppi :leveys 20 :fmt #(str/capitalize (name %))}
       {:otsikko "Selite" :tyyppi :string :nimi :selite :leveys 20}
       {:otsikko "Hinta" :tyyppi :numero :nimi :hinta :fmt (partial fmt/euro-opt true) :leveys 10}
       {:otsikko "Ylläpitoluokka" :tyyppi :string :nimi :yllapitoluokka
        :hae #(when (:yllapitoluokka %) (get-in % [:yllapitoluokka :nimi]))
        :leveys 10}
       {:otsikko "Laskentakohde" :tyyppi :string :nimi :laskentakohde :fmt second :leveys 10}]
      valitut-toteumat]
     [yhteenveto valitut-toteumat @valittu-kustannustyyppi]]))

(defn- muut-tyot-paakomponentti [e! tila]
  ;; Kun näkymään tullaan, yhdistetään navigaatiosta tulevat valinnat
  (e! (tiedot/->YhdistaValinnat @tiedot/valinnat))

  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->YhdistaValinnat uusi))))
    (fn [e! {:keys [valittu-toteuma] :as tila}]
      [:span
       (if valittu-toteuma
         [muu-tyo-lomake e! tila {:valittu-urakka @nav/valittu-urakka
                                  :valittu-sopimusnumero @u/valittu-sopimusnumero}]
         [muut-tyot-lista e! tila
          {:valittu-urakka @nav/valittu-urakka
           :valittu-sopimusnumero u/valittu-sopimusnumero
           :valitse-sopimusnumero u/valitse-sopimusnumero!
           :valitun-urakan-hoitokaudet u/valitun-urakan-hoitokaudet
           :valittu-hoitokausi u/valittu-hoitokausi
           :valitse-hoitokausi u/valitse-hoitokausi!
           :valittu-kustannustyyppi tiedot/valittu-kustannustyyppi
           :valitse-kustannustyyppi tiedot/valitse-kustannustyyppi!
           :kustannustyypit tiedot/+kustannustyypit+}])])))

(defn muut-kustannukset []
  (komp/luo
    (fn []
      [tuck tiedot/muut-tyot muut-tyot-paakomponentti])))
