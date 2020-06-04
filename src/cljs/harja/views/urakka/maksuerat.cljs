(ns harja.views.urakka.maksuerat
  "Urakan 'Maksuerat' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan timeout alts!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.maksuerat :as maksuerat]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.yleiset :as yleiset]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<! reaction-writable]]))

(defn- tyyppi-enum->string-monikko [tyyppi]
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

(defn- sorttausjarjestys [tyyppi numero]
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

(defn- ryhmittele-maksuerat [rivit]
  (when rivit
    (let [otsikko (fn [rivi] (tyyppi-enum->string-monikko (:tyyppi (:maksuera rivi))))
          otsikon-mukaan (group-by otsikko (sort-by
                                             #(sorttausjarjestys
                                               (:tyyppi (:maksuera %))
                                               (:numero %))
                                             rivit))]
      (doall (mapcat (fn [[otsikko rivit]]
                       (concat [(grid/otsikko otsikko)] rivit))
                     (seq otsikon-mukaan))))))

(defn- rakenna-kuittausta-odottavat-maksuerat [maksuerat]
  (into #{}
        (mapv
          (fn [rivi] (:numero rivi))
          (filter
            (fn [rivi]
              (or (= (:tila (:maksuera rivi)) :odottaa_vastausta)
                  (= (:tila (:kustannussuunnitelma rivi)) :odottaa_vastausta)))
            maksuerat))))

(def maksuerarivit (reaction-writable (ryhmittele-maksuerat @maksuerat/maksuerat)))
(def kuittausta-odottavat-maksuerat (reaction-writable (rakenna-kuittausta-odottavat-maksuerat @maksuerat/maksuerat)))

(def pollataan-kantaa? (atom false))


(defn- rakenna-paivittyneet-maksuerat [paivittyneiden-maksuerien-tilat]
  (mapv (fn [uusi-maksuera]
          (let [m (first (filter (fn [maksuera]
                                   (= (:numero maksuera) (:numero uusi-maksuera))) @maksuerat/maksuerat))]
            (assoc-in (assoc-in m [:maksuera :tila] (:tila (:maksuera uusi-maksuera)))
                      [:kustannussuunnitelma :tila] (:tila (:kustannussuunnitelma uusi-maksuera)))))
        paivittyneiden-maksuerien-tilat))

(defn- odottanut-vastausta-liian-kauan?
  "Palauttaa true jos annettu maksuerä tai kustannnussuunnitelma on ollut lähetyksessä yli tunnin."
  [lahetystiedot]
  (let [max-odotusaika (t/hours 1)
        lahetyksessa? (= (:tila lahetystiedot) :odottaa_vastausta)
        lahetetty (:lahetetty lahetystiedot)]


    (if (and lahetyksessa? lahetetty)
      (t/after? (pvm/nyt)
                (t/plus lahetetty max-odotusaika))
      false)))

(defn- hae-lahetettavat-maksueranumerot [maksuerat kuittausta-odottavat-maksuerat]
  (into #{} (filter
              #(or (not (contains? kuittausta-odottavat-maksuerat (:numero %)))
                   (and (contains? kuittausta-odottavat-maksuerat (:numero %))
                        (or (odottanut-vastausta-liian-kauan? (:maksuera %))
                            (odottanut-vastausta-liian-kauan? (:kustannussuunnitelma %)))))
              maksuerat)))

(defn- rakenna-samana-pysyneet-maksuerat [lahetettavat-maksueranumerot maksuerat]
  (filter #(not (lahetettavat-maksueranumerot (:numero %))) maksuerat))

(defn- rakenna-uudet-maksuerat [samana-pysyneet paivittyneet-maksuerat]
  (sort-by :numero (apply merge samana-pysyneet paivittyneet-maksuerat)))

(defn- rakenna-uudet-kuittausta-odottavat-maksuerat [lahetettavat-maksueranumerot kuittausta-odottavat-maksuerat]
  (into #{} (remove (set lahetettavat-maksueranumerot) kuittausta-odottavat-maksuerat)))

(defn- kasittele-onnistunut-siirto [uudet-maksuerat]
  (reset! maksuerat/maksuerat uudet-maksuerat))

(defn- kasittele-epaonnistunut-siirto [lahetetetyt-maksueranumerot uudet-kuittausta-odottavat]
  (do (reset! maksuerarivit (mapv (fn [rivi]
                                    (if (contains? lahetetetyt-maksueranumerot (:numero rivi))
                                      (assoc rivi :tila :virhe)
                                      rivi))
                                  @maksuerarivit))
      (reset! kuittausta-odottavat-maksuerat uudet-kuittausta-odottavat)))

(defn- laheta-maksuerat [maksuerat urakka-id]
  (let [lahetettavat-maksueranumerot (hae-lahetettavat-maksueranumerot maksuerat @kuittausta-odottavat-maksuerat)]
    (go (reset! kuittausta-odottavat-maksuerat (into #{} (clojure.set/union @kuittausta-odottavat-maksuerat lahetettavat-maksueranumerot)))
        (let [vastaus (<! (maksuerat/laheta-maksuerat lahetettavat-maksueranumerot urakka-id))
              paivittyneet-maksuerat (rakenna-paivittyneet-maksuerat vastaus)
              samana-pysyneet (rakenna-samana-pysyneet-maksuerat lahetettavat-maksueranumerot @maksuerat/maksuerat)
              uudet-maksuerat (rakenna-uudet-maksuerat samana-pysyneet paivittyneet-maksuerat)
              uudet-kuittausta-odottavat (rakenna-uudet-kuittausta-odottavat-maksuerat lahetettavat-maksueranumerot @kuittausta-odottavat-maksuerat)]
          (if (k/virhe? vastaus)
            (do
              (kasittele-epaonnistunut-siirto lahetettavat-maksueranumerot uudet-kuittausta-odottavat)
              (viesti/nayta! "Lähetys epäonnistui" :warning viesti/viestin-nayttoaika-lyhyt))
            (kasittele-onnistunut-siirto uudet-maksuerat))))))

(defn- pollaa-kantaa
  "Jos on olemassa maksueriä tai kustannussuunnitelmia, jotka odottavat kuittausta, hakee uusimmat tiedot kannasta. Muussa tapauksessa lopettaa pollauksen."
  []
  (when-not @pollataan-kantaa?
    (log "[MAKSUERAT] Käynnistetään pollaus.")
    (reset! pollataan-kantaa? true)
    (go-loop []
      (when @pollataan-kantaa?
        (<! (timeout 10000)) ;; Älä pollaa heti näkymään tultaessa
        (when (not (empty? @kuittausta-odottavat-maksuerat))
          (let [urakka-id-haettaessa (:id @nav/valittu-urakka)
                _ (log "[MAKSUERAT] Pollataan uudet maksuerät urakalle " urakka-id-haettaessa)
                result (<! (maksuerat/hae-urakan-maksuerat urakka-id-haettaessa))
                urakka-id-nyt (:id @nav/valittu-urakka)]
            (if (= urakka-id-nyt urakka-id-haettaessa)
              (do (log "[MAKSUERAT] Pollattu uudet maksuerät urakalle " urakka-id-nyt)
                  (reset! maksuerat/maksuerat result))
              (log "[MAKSUERAT] Maksuerät pollattu, mutta urakka vaihtui. Hylätään."))))
        (recur)))))

(defn- lopeta-pollaus []
  (log "[MAKSUERAT] Lopetetaan pollaus.")
  (reset! pollataan-kantaa? false))

(defn nayta-tila [tila lahetetty]
  (case tila
    :odottaa_vastausta [:span.tila-odottaa-vastausta "Lähetetty, odottaa kuittausta" [yleiset/ajax-loader-pisteet]]
    :lahetetty [:span.tila-lahetetty (if (not (nil? lahetetty))
                                       (str "Lähetetty, kuitattu " (pvm/pvm-aika lahetetty))
                                       (str "Lähetetty, kuitattu (kuittauspäivämäärää puuttuu)"))]
    :virhe [:span.tila-virhe "Lähetys epäonnistui!"]
    [:span "Ei lähetetty"]))

(defn maksuerat-listaus
  "Maksuerien pääkomponentti"
  []

  (komp/luo
    (komp/sisaan #(pollaa-kantaa))
    (komp/ulos #(lopeta-pollaus))
    (komp/lippu maksuerat/nakymassa?)
    (fn []
      (let [urakka-id (:id @nav/valittu-urakka)
            kuittausta-odottavat @kuittausta-odottavat-maksuerat
            maksuerarivit @maksuerarivit
            maksuerarivit-ilman-otsikkoja (filter (fn [rivi]
                                                    (not (contains? rivi :teksti)))
                                                  maksuerarivit)]
        [:div
         [grid/grid
          {:otsikko "Maksuerät"
           :tyhja (if (nil? maksuerarivit) [ajax-loader "Maksueriä haetaan..."] "Ei maksueriä")
           :tallenna nil
           :tunniste :numero}
          [{:otsikko "Numero" :nimi :numero :tyyppi :numero :leveys "9%" :pituus 16
            :hae (fn [rivi] (:numero rivi)) :tasaa :oikea}
           {:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "33%" :pituus 16
            :hae (fn [rivi] (:nimi (:maksuera rivi)))}
           {:otsikko "Kust.suunnitelman summa" :nimi :kustannussuunnitelma-summan :tyyppi :numero :leveys "16%"
            :fmt fmt/euro-opt :hae (fn [rivi] (:summa (:kustannussuunnitelma rivi))) :tasaa :oikea}
           {:otsikko "Maksuerän summa" :nimi :maksueran-summa :tyyppi :numero :leveys "14%" :pituus 16
            :fmt fmt/euro-opt :hae (fn [rivi] (:summa (:maksuera rivi))) :tasaa :oikea}
           {:otsikko "Kust.suunnitelman tila" :nimi :kustannussuunnitelma-tila :tyyppi :komponentti
            :komponentti (fn [rivi] (nayta-tila (:tila (:kustannussuunnitelma rivi)) (:lahetetty (:kustannussuunnitelma rivi)))) :leveys "19%"}
           {:otsikko "Maksuerän tila" :nimi :tila :tyyppi :komponentti
            :komponentti (fn [rivi] (nayta-tila (:tila (:maksuera rivi)) (:lahetetty (:maksuera rivi)))) :leveys "19%"}
           {:otsikko "Lähetys Sampoon" :nimi :laheta :tyyppi :komponentti
            :komponentti
            (fn [rivi]
              (let [maksuera-odottanut-liian-kauan (odottanut-vastausta-liian-kauan? (:maksuera rivi))
                    kustannussuunnitelma-odottanut-liian-kauan (odottanut-vastausta-liian-kauan? (:kustannussuunnitelma rivi))
                    maksueranumero (:numero rivi)]
                [:button.nappi-ensisijainen.nappi-grid
                 (merge
                   (when (and (contains? kuittausta-odottavat maksueranumero)
                              (not maksuera-odottanut-liian-kauan)
                              (not kustannussuunnitelma-odottanut-liian-kauan))
                     {:disabled true})
                   {:type "button"
                    :on-click #(laheta-maksuerat #{maksueranumero} urakka-id)})
                 "Lähetä"]))
            :leveys "10%"}]
          maksuerarivit]
         [yleiset/vihje (str "Lähetetyt maksuerät näkyvät Sampossa seuraavana päivänä klo. 12."
                             " Jos maksuerät pitää saada Sampoon nopeammin, ne on täytettävä Sampoon käsin.")]

         [:button.nappi-ensisijainen
          {:class (if (= (count kuittausta-odottavat)
                         (count maksuerarivit-ilman-otsikkoja))
                    "disabled"
                    "")
           :disabled (nil? maksuerarivit)
           :on-click #(do (.preventDefault %)
                          (laheta-maksuerat
                            (into #{} (map :numero maksuerarivit-ilman-otsikkoja))
                            urakka-id))}
          "Lähetä kaikki"]]))))
