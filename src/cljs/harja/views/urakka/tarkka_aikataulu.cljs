(ns harja.views.urakka.tarkka-aikataulu
  "Ylläpidon urakoiden kohteen tarkka aikataulu"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.domain.yllapitokohde :as ypk]
            [harja.tiedot.urakka.tarkka-aikataulu :as tiedot]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.aikataulu :as aikataulu-tiedot]
            [cljs-time.core :as t])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- kohteen-aikataulutaulukko [{:keys [yllapitokohde-id aikataulurivit sopimus-id
                                          vuosi voi-tallentaa? otsikko urakka-id]}]
  [grid/grid
   {:otsikko otsikko
    :tyhja "Ei aikataulua"
    :tallenna (if voi-tallentaa?
                #(tiedot/tallenna-aikataulu
                   {:rivit %
                    :urakka-id urakka-id
                    :sopimus-id sopimus-id
                    :vuosi vuosi
                    :yllapitokohde-id yllapitokohde-id
                    :onnistui-fn (fn [vastaus]
                                   (reset! aikataulu-tiedot/aikataulurivit vastaus))
                    :epaonnistui-fn (fn []
                                      (viesti/nayta! "Talennus epäonnistui!" :danger))})
                :ei-mahdollinen)}
   [{:otsikko "Toimenpide"
     :leveys 10
     :nimi :toimenpide
     :tyyppi :valinta
     :validoi [[:ei-tyhja "Anna toimenpide"]]
     :valinnat ypk/tarkan-aikataulun-toimenpiteet
     :valinta-nayta #(if % (ypk/tarkan-aikataulun-toimenpide-fmt %) "- valitse -")
     :fmt ypk/tarkan-aikataulun-toimenpide-fmt
     :pituus-max 128}
    {:otsikko "Kuvaus"
     :leveys 10
     :nimi :kuvaus
     :tyyppi :string
     :pituus-max 1024}
    {:otsikko "Alku"
     :leveys 5
     :nimi :alku
     :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
     :tyyppi :pvm
     :validoi [[:ei-tyhja "Anna alku"]]}
    {:otsikko "Loppu"
     :leveys 5
     :nimi :loppu
     :tyyppi :pvm
     :fmt #(pvm/pvm-ilman-samaa-vuotta % vuosi)
     :validoi [[:ei-tyhja "Anna loppu"]
               [:pvm-kentan-jalkeen :alku "Lopun on oltava alun jälkeen"]]}]
   aikataulurivit])

(defn tarkka-aikataulu [{:keys [rivi vuosi voi-muokata-paallystys? sopimus-id
                                voi-muokata-tiemerkinta? nakyma urakka-id]}]
  ;; Teknisesti tämä toimii niin, että näkymän mukaan asetetaan samaan urakkaan kuuluvat tarkat aikataulurivit
  ;; joko päällystys- tai tiemerkintätaulukkoon. Muut rivit näytetään toisessa taulukossa. Tämä toimii, sillä
  ;; tarkkoja aikatauluja voi luoda ainoastaan päällystys tai tiemerkintä tyyppisestä urakasta.
  (let [tarkka-aikataulu (:tarkka-aikataulu rivi)]
    [:div
     [kohteen-aikataulutaulukko
      {:otsikko "Kohteen päällystysurakan tarkka aikataulu"
       :yllapitokohde-id (:id rivi)
       :aikataulurivit (filter #(or (and (= nakyma :paallystys) (= (:urakka-id %) urakka-id))
                                    (and (not= nakyma :paallystys) (not= (:urakka-id %) urakka-id)))
                               (:tarkka-aikataulu rivi))
       :vuosi vuosi
       :voi-tallentaa? voi-muokata-paallystys?
       :sopimus-id sopimus-id
       :urakka-id urakka-id}]
     ;; Asiakkaan edustajan mukaan ei tällä hetkellä ole tarvetta tiemerkinnän tarkalle aikataululle.
     ;; Voidaan kuitenkin helposti ottaa käyttöön, mikäli tarve tulee (riittää tämän osion käyttöönotto)
     ;; Tarkka aikataulu tallentuu aina urakkakohtaisesti.
     #_[kohteen-aikataulutaulukko
      {:otsikko "Kohteen tiemerkintäurakan tarkka aikataulu"
       :yllapitokohde-id (:id rivi)
       :aikataulurivit (filter #(or (and (= nakyma :tiemerkinta) (= (:urakka-id %) urakka-id))
                                    (and (not= nakyma :tiemerkinta) (not= (:urakka-id %) urakka-id)))
                               (:tarkka-aikataulu rivi))
       :vuosi vuosi
       :voi-tallentaa? voi-muokata-tiemerkinta?
       :sopimus-id sopimus-id
       :urakka-id urakka-id}]]))
