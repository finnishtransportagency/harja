(ns harja.views.hallinta.kayttajat
  "Käyttäjähallinnan näkymä"
  (:require [reagent.core :refer [atom] :as re]
            [cljs.core.async :refer [<! chan]]

            [harja.tiedot.kayttajat :as k]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [bootstrap :as bs]
            
            [harja.loki :refer [log]]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

;; Tietokannan rooli enumin selvempi kuvaus
(def +rooli->kuvaus+
  {"jarjestelmavastuuhenkilo" "Järjestelmävastuuhenkilö"
   "tilaajan kayttaja" " Tilaajan käyttäjä"
   "urakanvalvoja" "Urakanvalvoja"
   "vaylamuodon vastuuhenkilo" "Väylämuodon vastuuhenkilö"
   "liikennepäivystäjä" "Liikennepäivystäjä"
   "tilaajan asiantuntija" "Tilaajan asiantuntija"
   "tilaajan laadunvalvontakonsultti" "Tilaajan laadunvalvontakonsultti"
   "urakoitsijan paakayttaja" "Urakoitsijan pääkäyttäjä"
   "urakoitsijan urakan vastuuhenkilo" "Urakoitsijan urakan vastuuhenkilö"
   "urakoitsijan kayttaja" "Urakoitsijan käyttäjä"
   "urakoitsijan laatuvastaava" "Urakoitsijan laatuvastaava"})

(def valittu-kayttaja (atom nil))

(defn kayttajaluettelo
  "Käyttäjälistauskomponentti"
  []
  (let [haku (atom "")
        sivu (atom 0)
        sivuja (atom 0)
        kayttajat (atom nil)]

    ;; Haetaan sivun ja datan perusteella, hakee uudestaan jos data muuttuu
    (run! (let [haku @haku
                sivu @sivu]
            (go (let [[lkm data] (<! (k/hae-kayttajat haku (* sivu 50) 50))]
                  (reset! sivuja (int (js/Math.ceil (/ lkm 50))))
                  (reset! kayttajat data)))))

    (fn []
      [grid/grid
       {:otsikko "Käyttäjät"
        :tyhja "Ei käyttäjiä."
        :rivi-klikattu #(reset! valittu-kayttaja %)
        }
       
       [{:otsikko "Nimi" :hae #(str (:etunimi %) " " (:sukunimi %)) :leveys "30%"}
        {:otsikko "Organisaatio" :nimi :org-nimi
         :hae #(:nimi (:organisaatio %))
         :leveys "30%"}

        {:otsikko "Roolit" :nimi :roolit
         :fmt #(str/join ", " (map +rooli->kuvaus+ %))
         :leveys "40%"}
        ]
       
       @kayttajat]
      
      
      )))

(defn kayttajatiedot [k]
  (let [tyyppi (case (:tyyppi (:organisaatio k))
                 (:hallintayksikko :liikennevirasto) :tilaaja
                 :urakoitsija :urakoitsija
                 nil)
        valittu-tyyppi (atom nil)
        roolit (atom (into #{} (:roolit k)))
        toggle-rooli! (fn [r]
                        (swap! roolit (fn [roolit]
                                        (if (roolit r)
                                          (disj roolit r)
                                          (conj roolit r)))))
        roolivalinta (fn [rooli & sisalto]
                       (let [valittu (@roolit rooli)]
                         [:div.rooli
                          [:div.roolivalinta
                           [:input {:type "checkbox" :checked valittu
                                    :on-change #(toggle-rooli! rooli %)
                                    :name rooli}]
                           [:label {:for rooli
                                    :on-click #(toggle-rooli! rooli %)} (+rooli->kuvaus+ rooli)]]
                          (when (and valittu (not (empty? sisalto)))
                            [:div.rooli-lisavalinnat
                             sisalto])]))
        ]
    (fn [k]
      (log "K: " (pr-str k))
      [:div.kayttajatiedot
       [:button.btn.btn-default {:on-click #(reset! valittu-kayttaja nil)}
        (ikonit/chevron-left) " Takaisin käyttäjäluetteloon"]
     
       [:h3 "Muokkaa käyttäjää " (:etunimi k) " " (:sukunimi k)]
     
       [bs/panel
        {} "Perustiedot"
        [yleiset/tietoja
         {}
         "Nimi:" [:span.nimi (:etunimi k) " " (:sukunimi k)]
         "Sähköposti:" [:span.sahkoposti (:sahkoposti k)]
         "Puhelinnumero:" [:span.puhelin (:puhelin k)]]]
     
       [:form.form-horizontal

        ;; Valitaan käyttäjän tyyppi
        [:div.form-group
         [:label.col-sm-2.control-label {:for "kayttajatyyppi"}
          "Käyttäjätyyppi"]
         [:div.col-sm-10
          (if tyyppi
            [:span (case tyyppi
                     :tilaaja "Tilaaja"
                     :urakoitsija "Urakoitsija")]
            [:span
             [:input {:name "kayttajatyyppi" :type "radio" :value "tilaaja" :checked (= :tilaaja @valittu-tyyppi)} " Tilaaja"]
             [:input {:name "kayttajatyyppi" :type "radio" :value "urakoitsija" :checked (= :urakoitsija @valittu-tyyppi)} " Urakoitsija"]])]]

        ;; Käyttäjän roolit
        [:div.form-group
         [:label.col-sm-2.control-label
          "Roolit:"]
         [:div.col-sm-10.roolit
          (if (= tyyppi :tilaaja)
            [:span
             [roolivalinta "jarjestelmavastuuhenkilo"]
             [roolivalinta "tilaajan kayttaja"]
             [roolivalinta "urakanvalvoja"
              ^{:key "urakat"}
              [:div "URAKAT TÄNNE"]]
             [roolivalinta "vaylamuodon vastuuhenkilo"
              ^{:key "vaylamuoto"}
              [:div "väylämuoto"]]
             [roolivalinta "tilaajan asiantuntija"]
             [roolivalinta "tilaajan laadunvalvontakonsultti"]]

            ;; urakoitsijan roolit
            [:span
             [roolivalinta "urakoitsijan paakayttaja"]
             [roolivalinta "urakoitsijan urakan vastuuhenkilo"]
             [roolivalinta "urakoitsijan kayttaja"]
             [roolivalinta "urakoitsijan laatuvastaava"]]
            )]]
       
        ]])))
              
(defn kayttajat
  "Käyttäjähallinnan pääkomponentti"
  []
  (if-let [vk @valittu-kayttaja]
    [kayttajatiedot vk]
    [kayttajaluettelo]))

