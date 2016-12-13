(ns harja.views.kartta.infopaneeli
  "Muodostaa kartalle overlayn, joka sisältää klikatussa koordinaatissa
  olevien asioiden tiedot."
  (:require [harja.ui.komponentti :as komp]
            [reagent.core :refer [atom] :as r]
            [cljs.core.async :as async]
            [harja.loki :refer [log tarkkaile! error] :as log]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.napit :as napit]
            [harja.ui.debug :refer [debug]]
            [harja.ui.kentat :as kentat]
            [harja.ui.kartta.infopaneelin-sisalto :as infopaneelin-sisalto]
            [harja.ui.ikonit :as ikonit])
  (:require-macros
   [cljs.core.async.macros :as async-macros]))

(def testidata
  {:haetaan? true
   :koordinaatti [20 20]
   :asiat [{:otsikko "Toimenpidepyyntö 20.12.2016 15:55:15"
            :tiedot [{:otsikko "Ilmoitettu" :tyyppi :pvm-aika :nimi :ilmoitettu}
                     {:otsikko "Kuittaukset" :tyyppi :positiivinen-numero :hae #(constantly 5)}]
            :data {:ilmoitettu (harja.pvm/nyt)}}
           {:otsikko "Auraus 15km"
            :tiedot [{:otsikko "Hyvää työtä" :tyyppi :radio :nimi :hyvaa-tyota?}
                     {:otsikko "Toimenpide" :tyyppi :tierekisteriosoite :nimi :tr }]
            :data {:hyvaa-tyota? true
                   :tr {:numero 20 :alkuosa 1 :alkuetaisyys 1 :loppuosa 2 :loppuetaisyys 200}}}]})

;; kun asiat-pisteessä :haetaan = true, täämän pitisi resetoitua
(defonce valittu-asia (atom nil))

(defn esita-otsikko [{:keys [otsikko] :as asia}]
  [:div.ip-osio
   [:div.ip-otsikko
    {:on-click #(reset! valittu-asia asia)}
    [:span.ip-haitari-otsikko otsikko]]])

(defn- kentan-arvo [skeema data]
  (let [arvo-fn (or (:hae skeema) (:nimi skeema))]
    ;; Kentat namespace olettaa, että kentän arvo tulee atomissa
    (when arvo-fn
      (r/wrap (arvo-fn data)
              #(log/error "Infopaneelissa ei voi muokata tietoja: " %)))))

(defn esita-yksityiskohdat [{:keys [otsikko tiedot data tyyppi] :as asia} linkin-kasittely-fn]
  (if-not (or (keyword? tyyppi) (fn? tyyppi))
    (do
      (error "esita-yksityiskohdat: asian tyyppi huono, asia: " (clj->js asia))
      nil)
    ;; else
    [:div.ip-osio
     [:span.ip-otsikko otsikko]
     (when-let [{:keys [teksti toiminto]} (tyyppi linkin-kasittely-fn)]
       [:span [napit/yleinen teksti #(toiminto data)]])
     (for [[idx kentan-skeema] (map-indexed #(do [%1 %2]) tiedot)]
       ^{:key (str "infopaneelin_yksityiskohta_" idx)}
       [:div
        [:label.control-label
         [:span
          [:span.kentan-label (:otsikko kentan-skeema)]]]
        [kentat/nayta-arvo kentan-skeema (kentan-arvo kentan-skeema data)]])]))

(defn- map-ilman-funktioita [m]
  (select-keys m (for [[k v] m :when (not (fn? v))] k)))

(defn- sama-asia? [a b]
  (let [vertailumuoto (fn [v] (assoc v :tiedot (map map-ilman-funktioita (:tiedot v))))]
    (= (vertailumuoto a) (vertailumuoto b))))

(defn infopaneeli [asiat-pisteessa piilota-fn! linkkifunktiot]

  (let [{:keys [asiat haetaan? koordinaatti]} asiat-pisteessa
        asiat (infopaneelin-sisalto/skeemamuodossa asiat)
        vain-yksi-asia? (-> asiat count (= 1))
        useampi-asia? (not vain-yksi-asia?)
        esita-yksityiskohdat? (or @valittu-asia vain-yksi-asia?)
        ainoa-asia (when vain-yksi-asia? (first asiat))
        valittu-asia-ok? (some #(sama-asia? @valittu-asia %) asiat)]
    ;; tyhjennetään valinta kun valinnassa on asia joka ei esiinny @asiat-pisteessa
    ;; (esim latauksen aikana)
    (when-not valittu-asia-ok?
      (do
        (when (and @valittu-asia asiat)
          (log "@valittu-asia sisältää" (clj->js @valittu-asia))
          (log "tyhjennetään @valittu-asia koska ei esiinny atomissa @asiat-pisteessa, esim" (clj->js (first asiat))))
        (reset! valittu-asia nil)))
    [:div#kartan-infopaneeli.kartan-infopaneeli
     #_[debug asiat-pisteessa]
     [:div
      (when (and @valittu-asia useampi-asia?)
        [napit/takaisin "" #(reset! valittu-asia nil)])
      (when haetaan?
        [ajax-loader])
      [:button.close {:on-click piilota-fn!
                      :type     "button"}
       [ikonit/remove]]]

     (when-not (empty? asiat)
       (if esita-yksityiskohdat?
         [esita-yksityiskohdat (or @valittu-asia ainoa-asia) @linkkifunktiot]
         ;; else
         [:div (doall (for [[idx asia] (map-indexed #(do [%1 %2]) asiat)]
                        ^{:key (str "infopaneelin_otsikko_" idx)}
                        [esita-otsikko asia]))]))]))
