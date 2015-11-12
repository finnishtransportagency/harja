(ns harja.views.urakka.maksuerat
  "Urakan 'Maksuerat' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan timeout alts!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.maksuerat :as maksuerat]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn tyyppi-enum->string-monikko [tyyppi]
  (case tyyppi
    :kokonaishintainen "Kokonaishintaiset"
    :yksikkohintainen "Yksikköhintaiset"
    :lisatyo "Lisätyöt"
    :indeksi "Indeksit"
    :bonus "Bonukset"
    :sakko "Sakot"
    :akillinen-hoitotyo "Äkilliset hoitotyöt"
    :muu "Muut"
    "Ei tyyppiä"))

(defn sorttausjarjestys [tyyppi numero]
  (let [tyyppi-jarjestysnumero (case tyyppi
                                 :kokonaishintainen 1
                                 :yksikkohintainen 2
                                 :lisatyo 3
                                 :indeksi 4
                                 :bonus 5
                                 :sakko 6
                                 :akillinen-hoitotyo 7
                                 :muu 8
                                 9)]
    [tyyppi-jarjestysnumero numero]))

(defn ryhmittele-maksuerat [rivit]
  (let [otsikko (fn [rivi] (tyyppi-enum->string-monikko (:tyyppi (:maksuera rivi))))
        otsikon-mukaan (group-by otsikko (sort-by
                                           #(sorttausjarjestys
                                                    (:tyyppi (:maksuera %))
                                                    (:numero %))
                                           rivit))]
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

(def maksuerarivit (reaction (ryhmittele-maksuerat @maksuerat/maksuerat)))
(def kuittausta-odottavat-maksuerat (reaction (rakenna-kuittausta-odottavat-maksuerat @maksuerat/maksuerat)))

(def pollataan-kantaa? (atom false))


(defn rakenna-paivittyneet-maksuerat [paivittyneiden-maksuerien-tilat]
  (mapv (fn [uusi-maksuera]
          (let [m (first (filter (fn [maksuera]
                                   (= (:numero maksuera) (:numero uusi-maksuera))) @maksuerat/maksuerat))]
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
  (reset! maksuerat/maksuerat uudet-maksuerat))

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
              samana-pysyneet (rakenna-samana-pysyneet-maksuerat lahetettavat-maksueranumerot @maksuerat/maksuerat)
              uudet-maksuerat (rakenna-uudet-maksuerat samana-pysyneet paivittyneet-maksuerat)
              uudet-kuittausta-odottavat (rakenna-uudet-kuittausta-odottavat-maksuerat lahetettavat-maksueranumerot @kuittausta-odottavat-maksuerat)]
          (if vastaus
            (kasittele-onnistunut-siirto uudet-maksuerat)
            (kasittele-epaonnistunut-siirto lahetettavat-maksueranumerot uudet-kuittausta-odottavat))))))

(defn pollaa-kantaa
  "Jos on olemassa maksueriä tai kustannussuunnitelmia, jotka odottavat kuittausta, hakee uusimmat tiedot kannasta. Muussa tapauksessa lopettaa pollauksen."
  []
  (reset! pollataan-kantaa? true)
  (let [ur @nav/valittu-urakka]
    (go-loop []
      (when @pollataan-kantaa?
        (when (not (empty? @kuittausta-odottavat-maksuerat))          
          (let [result (<! (maksuerat/hae-urakan-maksuerat (:id ur)))]
            (log "tuli maksueriä: " result)
            (reset! maksuerat/maksuerat result)))
        (<! (timeout 10000))
        (recur)))))

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
  []
  (pollaa-kantaa)
  (komp/luo
    {:component-will-unmount
     (fn []
       (reset! pollataan-kantaa? false))}
    (fn []
      (let [kuittausta-odottavat @kuittausta-odottavat-maksuerat
            maksuerarivit @maksuerarivit
            maksuerarivit-ilman-otsikkoja  (filter (fn [rivi]
                                             (not (contains? rivi :teksti)))
                                           maksuerarivit)]
        [:div
         [grid/grid
          {:otsikko  "Maksuerät"
           :tyhja    "Ei maksueriä."
           :tallenna nil
           :tunniste :numero}
          [{:otsikko "Numero" :nimi :numero :tyyppi :numero :leveys "9%" :pituus 16
            :hae     (fn [rivi] (:numero rivi))}
           {:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "33%" :pituus 16
            :hae     (fn [rivi] (:nimi (:maksuera rivi)))}
           {:otsikko "Kust.suunnitelman summa" :nimi :kustannussuunnitelma-summan :tyyppi :numero :leveys "16%"
            :fmt     fmt/euro-opt :hae (fn [rivi] (:summa (:kustannussuunnitelma rivi)))}
           {:otsikko "Maksuerän summa" :nimi :maksueran-summa :tyyppi :numero :leveys "14%" :pituus 16
            :fmt     fmt/euro-opt :hae (fn [rivi] (:summa (:maksuera rivi)))}
           {:otsikko     "Maksueran tila" :nimi :tila :tyyppi :komponentti
            :komponentti (fn [rivi] (nayta-tila (:tila (:maksuera rivi)) (:lahetetty (:maksuera rivi)))) :leveys "19%"}
           {:otsikko     "Kust.suunnitelman tila" :nimi :kustannussuunnitelma-tila :tyyppi :komponentti
            :komponentti (fn [rivi] (nayta-tila (:tila (:kustannussuunnitelma rivi)) (:lahetetty (:kustannussuunnitelma rivi)))) :leveys "19%"}
           {:otsikko     "Lähetys Sampoon" :nimi :laheta :tyyppi :komponentti
            :komponentti (fn [rivi]
                           (let [maksueranumero (:numero rivi)]
                             [:button.nappi-ensisijainen.nappi-grid {:class    (str "nappi-ensisijainen " (if (contains? kuittausta-odottavat maksueranumero) "disabled"))
                                                       :type     "button"
                                                       :on-click #(laheta-maksuerat #{maksueranumero})} "Lähetä"]))
            :leveys      "10%"}]
          maksuerarivit]
       [:button.nappi-ensisijainen {:class    (if (= (count kuittausta-odottavat)
                                                     (count maksuerarivit-ilman-otsikkoja))
                                                "disabled"
                                                "")
                                    :on-click #(do (.preventDefault %)
                                                   (laheta-maksuerat
                                                     (into #{}
                                                           (map
                                                             (fn [rivi]
                                                               (:numero rivi))
                                                             maksuerarivit-ilman-otsikkoja))))} "Lähetä kaikki"]]))))
