(ns harja.views.urakka.maksuerat
  "Urakan 'Maksuerat' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.maksuerat :as maksuerat]
            [harja.tiedot.istunto :as istunto]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.urakka.toteumat.lampotilat :refer [lampotilat]]

            [harja.ui.visualisointi :as vis]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn tyyppi-enum->string-plural [tyyppi]
  (case tyyppi
    :kokonaishintainen "Kokonaishintaiset"
    :yksikkohintainen "Yksikköhintaiset"
    :lisatyo "Lisätyöt"
    :indeksi "Indeksit"
    :bonus "Bonukset"
    :sakko "Sakot"
    :akillinen_hoitotyo "Äkilliset hoitotyöt"
    :muu "Muut"
    "Ei tyyppiä"))

(defn sorttausjarjestys [tyyppi]
  (case tyyppi
    :kokonaishintainen 1
    :yksikkohintainen 2
    :lisatyo 3
    :indeksi 4
    :bonus 5
    :sakko 6
    :akillinen_hoitotyo 7
    :muu 8
    9))

(defn hae-urakan-maksuerat [urakka]
  (go
    (log (str "Urakan id: " (:id urakka)))
    (sort-by :numero (<! (maksuerat/hae-urakan-maksuerat (:id urakka))))))

(defn ryhmittele-maksuerat [rivit]
  (let [otsikko (fn [rivi] (tyyppi-enum->string-plural (:tyyppi (:maksuera rivi))))
        otsikon-mukaan (group-by otsikko (sort-by #(sorttausjarjestys (:tyyppi (:maksuera %))) rivit))]
    (doall (mapcat (fn [[otsikko rivit]]
                     (concat [(grid/otsikko otsikko)] rivit))
                   (seq otsikon-mukaan)))))

(defn rakenna-kuittausta-odottavat-maksuerat [maksuerat]
  (into #{}
        (mapv
          (fn [rivi] (:numero rivi))
          (filter
            (fn [rivi]
              (or (= (:tila (:maksuera rivi)) :odottaa_vastausta)
                  (= (:tila (:kustannussuunnitelma rivi)) :odottaa_vastausta)))
            maksuerat))))

(def urakka-id (atom nil))
(def maksuerat (reaction<! (hae-urakan-maksuerat @nav/valittu-urakka)))
(def maksuerarivit (reaction (ryhmittele-maksuerat @maksuerat)))
(def kuittausta-odottavat-maksuerat (reaction (rakenna-kuittausta-odottavat-maksuerat @maksuerat)))
(def pollaus-id (atom nil))
(def pollataan-kantaa? (atom false))

(declare aloita-pollaus)

(defn rakenna-paivittyneet-maksuerat [paivittyneiden-maksuerien-tilat]
  (mapv (fn [uusi-maksuera]
          (let [m (first (filter (fn [maksuera]
                                   (= (:numero maksuera) (:numero uusi-maksuera))) @maksuerat))]
            (assoc-in (assoc-in m [:maksuera :tila] (:tila (:maksuera uusi-maksuera)))
                      [:kustannussuunnitelma :tila] (:tila (:kustannussuunnitelma uusi-maksuera)))))
        paivittyneiden-maksuerien-tilat))

(defn hae-lahetettavat-maksueranumerot [maksueranumerot kuittausta-odottavat-maksuerat]
  (into #{} (filter #(not (contains? kuittausta-odottavat-maksuerat %)) maksueranumerot)))

(defn rakenna-samana-pysyneet-maksuerat [lahetettavat-maksueranumerot maksuerat]
  (filter #(not (lahetettavat-maksueranumerot (:numero %))) maksuerat))

(defn rakenna-uudet-maksuerat [samana-pysyneet paivittyneet-maksuerat]
  (sort-by :numero (apply merge samana-pysyneet paivittyneet-maksuerat)))

(defn rakenna-uudet-kuittausta-odottavat-maksuerat [lahetettavat-maksueranumerot kuittausta-odottavat-maksuerat]
  (into #{} (remove (set lahetettavat-maksueranumerot) kuittausta-odottavat-maksuerat)))

(defn kasittele-onnistunut-siirto [uudet-maksuerat]
  (do
    (reset! maksuerat uudet-maksuerat)
    (aloita-pollaus)))

(defn kasittele-epaonnistunut-siirto [lahetetetyt-maksueranumerot uudet-kuittausta-odottavat]
  (do (reset! maksuerarivit (mapv (fn [rivi]
                                    (if (contains? lahetetetyt-maksueranumerot (:numero rivi))
                                      (assoc rivi :tila :virhe)
                                      rivi))
                                  @maksuerarivit))
      (reset! kuittausta-odottavat-maksuerat uudet-kuittausta-odottavat)))

(defn laheta-maksuerat [maksueranumerot]
  (let [lahetettavat-maksueranumerot (hae-lahetettavat-maksueranumerot maksueranumerot @kuittausta-odottavat-maksuerat)]
    (go (reset! kuittausta-odottavat-maksuerat (into #{} (clojure.set/union @kuittausta-odottavat-maksuerat lahetettavat-maksueranumerot)))
        (let [vastaus (<! (maksuerat/laheta-maksuerat lahetettavat-maksueranumerot))
              paivittyneet-maksuerat (rakenna-paivittyneet-maksuerat vastaus)
              samana-pysyneet (rakenna-samana-pysyneet-maksuerat lahetettavat-maksueranumerot @maksuerat)
              uudet-maksuerat (rakenna-uudet-maksuerat samana-pysyneet paivittyneet-maksuerat)
              uudet-kuittausta-odottavat (rakenna-uudet-kuittausta-odottavat-maksuerat lahetettavat-maksueranumerot @kuittausta-odottavat-maksuerat)]
          (if vastaus
            (kasittele-onnistunut-siirto uudet-maksuerat)
            (kasittele-epaonnistunut-siirto lahetettavat-maksueranumerot uudet-kuittausta-odottavat))))))

(defn lopeta-pollaus []
  (reset! pollataan-kantaa? false)
  (log (str "Kannan pollaus lopetettiin. Pollaus-id: " (pr-str @pollaus-id)))
  (js/clearInterval @pollaus-id) (reset! pollaus-id nil))

(defn pollaa-kantaa
  "Jos on olemassa maksueriä tai kustannussuunnitelmia, jotka odottavat kuittausta, hakee uusimmat tiedot kannasta. Muussa tapauksessa lopettaa pollauksen."
  []
  (if (not (empty? @kuittausta-odottavat-maksuerat))
    (do
      (log (str "Pollataan kantaa. Pollaus-id: " (pr-str @pollaus-id)))
      (go (reset! maksuerat (<! (hae-urakan-maksuerat @urakka-id)))))
    (do (log "Lopetetaan pollaus (ei lähetyksessä olevia maksueriä)")
        (lopeta-pollaus))))

(defn aloita-pollaus
  "Aloittaa kannan pollaamisen jos pollaus ei jo ole käynnissä"
  []
  (if (false? @pollataan-kantaa?) (do
                                    (reset! pollataan-kantaa? true)
                                    (reset! pollaus-id (js/setInterval pollaa-kantaa 10000))
                                    (log (str "Aloitettiin pollaus 10s välein. Pollaus-id: " (pr-str @pollaus-id))))))

(defn nayta-tila [tila lahetetty]
  (case tila
    :odottaa_vastausta [:span.maksuera-odottaa-vastausta "Lähetetty, odottaa kuittausta" [yleiset/ajax-loader-pisteet]]
    :lahetetty [:span.maksuera-lahetetty (if (not (nil? lahetetty))
                                           (str "Lähetetty, kuitattu " (pvm/pvm-aika lahetetty))
                                           (str "Lähetetty, kuitattu (kuittauspäivämäärää puuttuu)"))]
    :virhe [:span.maksuera-virhe "Lähetys epäonnistui!"]
    [:span "Ei lähetetty"]))

(defn maksuerat-listaus
  "Maksuerien pääkomponentti"
  [ur]
  (reset! urakka-id ur)
  (hae-urakan-maksuerat ur)
  (aloita-pollaus)
  (komp/luo
    {:component-will-unmount
     (fn []
       (lopeta-pollaus))}
    (fn []
      [:div
       (let [kuittausta-odottavat @kuittausta-odottavat-maksuerat]
         [grid/grid
          {:otsikko  "Maksuerät"
           :tyhja    "Ei maksueriä."
           :tallenna nil
           :tunniste :numero}
          [{:otsikko     "Numero" :nimi :numero :tyyppi :komponentti :leveys "10%" :pituus 16
            :komponentti (fn [rivi] (:numero rivi))}
           {:otsikko     "Nimi" :nimi :nimi :tyyppi :komponentti :leveys "33%" :pituus 16
            :komponentti (fn [rivi] (:nimi (:maksuera rivi)))}
           {:otsikko     "Maksuerän summa" :nimi :maksueran-summa :tyyppi :komponentti :leveys "14%" :pituus 16
            :komponentti (fn [rivi] (:summa (:maksuera rivi)))}
           {:otsikko     "Kust.suunnitelman summa" :nimi :kustannussuunnitelma-summan :tyyppi :komponentti :leveys "18%"
            :komponentti (fn [rivi] (:summa (:kustannussuunnitelma rivi)))}
           {:otsikko     "Maksueran tila" :nimi :tila :tyyppi :komponentti
            :komponentti (fn [rivi] (nayta-tila (:tila (:maksuera rivi)) (:lahetetty (:maksuera rivi)))) :leveys "19%"}
           {:otsikko     "Kust.suunnitelman tila" :nimi :kustannussuunnitelma-tila :tyyppi :komponentti
            :komponentti (fn [rivi] (nayta-tila (:tila (:kustannussuunnitelma rivi)) (:lahetetty (:kustannussuunnitelma rivi)))) :leveys "19%"}
           {:otsikko     "Lähetys Sampoon" :nimi :laheta :tyyppi :komponentti
            :komponentti (fn [rivi]
                           (let [maksueranumero (:numero rivi)]
                             [:button.laheta-maksuera {:class    (str "nappi-ensisijainen " (if (contains? kuittausta-odottavat maksueranumero) "disabled"))
                                                       :type     "button"
                                                       :on-click #(laheta-maksuerat #{maksueranumero})} "Lähetä"]))
            :leveys      "7%"}]
          @maksuerarivit])
       [:button.nappi-ensisijainen {:class    (if (= (count @kuittausta-odottavat-maksuerat) (count @maksuerarivit)) "disabled" "")
                                    :on-click #(do (.preventDefault %)
                                                   (laheta-maksuerat (into #{} (mapv (fn [rivi] (:numero rivi)) @maksuerarivit))))} "Lähetä kaikki"]])))