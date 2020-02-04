(ns harja.views.hallinta.valtakunnalliset-valitavoitteet
  "Valtakunnallisten välitavoitteiden näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.valtakunnalliset-valitavoitteet :as tiedot]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.grid :refer [grid]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.valinnat :as valinnat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

;; Valtakunnallisille välitavoitteille on haluttu eri urakkatyypeissä käyttää hieman eri nimitystä
(def kertaluontoiset-otsikko {:tiemerkinta "Tiemerkinnän kertaluontoisten, määräaikaan mennessä tehtävien töiden mallipohjat"})
(def toistuvat-otsikko {:tiemerkinta "Tiemerkinnän vuosittain toistuvien, määräaikaan mennessä tehtävien töiden mallipohjat"})

(defn kertaluontoiset-valitavoitteet-grid
  [valitavoitteet-atom kertaluontoiset-valitavoitteet-atom valittu-urakkatyyppi-atom]
  [grid/grid
   {:otsikko (or (kertaluontoiset-otsikko (:arvo @valittu-urakkatyyppi-atom))
                 "Kertaluontoiset, kaikissa urakoissa määräaikaan mennessä tehtävät työt")
    :tyhja (if (nil? @kertaluontoiset-valitavoitteet-atom)
             [y/ajax-loader "Tavoitteita haetaan..."]
             "Ei kertaluontoisia määräaikaan mennessä tehtäviä töitä")
    :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/hallinta-valitavoitteet)
                #(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet
                                         (->> %
                                              (map (fn [valitavoite]
                                                     (-> valitavoite
                                                         (assoc :tyyppi :kertaluontoinen)
                                                         (assoc :urakkatyyppi
                                                                (:arvo @yhteiset/valittu-urakkatyyppi))))))))]
                       (if (k/virhe? vastaus)
                         (viesti/nayta! "Määräaikaan mennessä tehtävien töiden tallentaminen epännistui"
                                        :warning viesti/viestin-nayttoaika-keskipitka)
                         (reset! valitavoitteet-atom vastaus)))))}
   [{:otsikko "Nimi" :leveys 60 :nimi :nimi :tyyppi :string :pituus-max 128
     :validoi [[:ei-tyhja "Anna työn kuvaus"]]}
    {:otsikko "Taka\u00ADraja" :leveys 20 :nimi :takaraja :fmt #(if %
                                                                 (pvm/pvm-opt %)
                                                                 "Ei takarajaa")
     :tyyppi :pvm}]
   (sort-by :takaraja (filter #(= (:urakkatyyppi %)
                                  (:arvo @yhteiset/valittu-urakkatyyppi))
                              @kertaluontoiset-valitavoitteet-atom))])

(defn toistuvat-valitavoitteet-grid
  [valitavoitteet-atom toistuvat-valitavoitteet-atom valittu-urakkatyyppi-atom]
  [grid/grid
   {:otsikko (or (toistuvat-otsikko (:arvo @valittu-urakkatyyppi-atom))
                 "Vuosittain toistuvat, kaikissa urakoissa määräaikaan mennessä tehtävät työt")
    :tyhja (if (nil? @toistuvat-valitavoitteet-atom)
             [y/ajax-loader "Tavoitteita haetaan..."]
             "Ei toistuvia töitä")
    :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/hallinta-valitavoitteet)
                #(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet
                                         (->> %
                                              (map (fn [valitavoite]
                                                     (-> valitavoite
                                                         (assoc :tyyppi :toistuva)
                                                         (assoc :urakkatyyppi
                                                                (:arvo @yhteiset/valittu-urakkatyyppi))))))))]
                       (if (k/virhe? vastaus)
                         (viesti/nayta! "Määräaikaan mennessä tehtävien töiden tallentaminen epännistui"
                                        :warning viesti/viestin-nayttoaika-keskipitka)
                         (reset! valitavoitteet-atom vastaus)))))}
   [{:otsikko "Nimi" :leveys 60 :nimi :nimi :tyyppi :string :pituus-max 128
     :validoi [[:ei-tyhja "Anna työn kuvaus"]]}
    {:otsikko "Taka\u00ADrajan toisto\u00ADpäi\u00ADvä" :leveys 10 :nimi :takaraja-toistopaiva
     :tyyppi :numero :desimaalien-maara 0 :validoi [[:rajattu-numero 1 31 "Anna päivä välillä 1 - 31"]]}
    {:otsikko "Taka\u00ADrajan toisto\u00ADkuu\u00ADkausi" :leveys 10 :nimi :takaraja-toistokuukausi
     :tyyppi :numero :desimaalien-maara 0 :validoi [[:rajattu-numero 1 12 "Anna kuukausi välillä 1 - 12"]]}]
   (sort-by (juxt :takaraja-toistokuukausi :takaraja-toistopaiva)
            (filter #(= (:urakkatyyppi %)
                        (:arvo @yhteiset/valittu-urakkatyyppi))
                    @toistuvat-valitavoitteet-atom))])

(defn- suodattimet []
  [valinnat/urakkatyyppi
   yhteiset/valittu-urakkatyyppi
   nav/+urakkatyypit+
   #(reset! yhteiset/valittu-urakkatyyppi %)])

(defn valitavoitteet []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn []
      (let [nayta-kertaluontoiset-valtakunnalliset?
            (some? (tiedot/valtakunnalliset-kertaluontoiset-valitavoitteet-kaytossa
                                             (:arvo @yhteiset/valittu-urakkatyyppi)))
            nayta-toistuvat-valtakunnalliset?
            (some? (tiedot/valtakunnalliset-toistuvat-valitavoitteet-kaytossa
                     (:arvo @yhteiset/valittu-urakkatyyppi)))]

        [:div
         [suodattimet]
         (when nayta-kertaluontoiset-valtakunnalliset?
           [kertaluontoiset-valitavoitteet-grid
            tiedot/valitavoitteet
            tiedot/kertaluontoiset-valitavoitteet
            yhteiset/valittu-urakkatyyppi])
         [:br]
         (when nayta-toistuvat-valtakunnalliset?
           [toistuvat-valitavoitteet-grid
            tiedot/valitavoitteet
            tiedot/toistuvat-valitavoitteet
            yhteiset/valittu-urakkatyyppi])

         (if (or nayta-toistuvat-valtakunnalliset?
                 nayta-toistuvat-valtakunnalliset?)
           [yleiset/vihje-elementti
            [:span
             (when nayta-kertaluontoiset-valtakunnalliset?
               [:span
                "Uudet kertaluontoiset tavoitteet liitetään valitun urakkatyypin mukaan käynnissä oleviin ja tuleviin urakoihin, jos tavoitteen takaraja on urakan voimassaoloaikana."
                [:br]])
             (when nayta-toistuvat-valtakunnalliset?
               [:span
                "Uudet vuosittain toistuvat tavoitteet liitetään valitun urakkatyypin mukaan käynnissä oleviin ja tuleviin urakoihin. Tavoite liitetään kertaalleen urakan kaikkiin jäljellä oleviin urakkavuosiin."
                [:br]])
             (when (or nayta-toistuvat-valtakunnalliset? nayta-kertaluontoiset-valtakunnalliset?)
               [:span
                "Hoitourakan tavoitteisiin tehdyt lisäykset ja muutokset koskevat sekä uuden (MHU) että vanhan tyyppisiä hoitourakoita."
                [:br]])

             [:br]

             (when nayta-kertaluontoiset-valtakunnalliset?
               [:span
                "Kertaluontoisen tavoitteen päivittäminen päivittää tiedot urakoihin, jos tavoitetta ei ole muokattu urakassa."
                [:br]"Jos tavoitetta on urakassa muokattu, hallintaosiossa päivitetty tavoite lisätään urakkaan uutena tavoitteena."
                [:br]])

             (when nayta-toistuvat-valtakunnalliset?
               [:span
                "Vuosittain toistuvan tavoitteen muokkaaminen päivittää sen käynnissä oleviin ja tuleviin urakoihin kertaalleen per jäljellä oleva urakkavuosi, ellei sitä ole muokattu urakassa."
                [:br]"Jos tavoitetta on urakassa muokattu, päivitetty tavoite lisätään urakkaan uutena tavoitteena jäljellä oleville urakkavuosille."
                [:br]])

             [:br]

             "Poistettu tavoite jää näkyviin päättyneisiin urakoihin sekä käynnissä oleviin urakoihin, joissa se on ehditty merkitä valmiiksi."]]
           [:div "Valtakunnallisia määräaikaan mennessä tehtäviä töitä ei määritellä valitussa urakkatyypissä."])]))))
