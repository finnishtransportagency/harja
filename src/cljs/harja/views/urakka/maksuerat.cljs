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
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn maksuerat
  "Maksuerien pääkomponentti"
    [ur]
    (let [lahetyksessa (atom #{}) ; Setti lähetyksessä olevista maksuerien numeroista
          maksuerarivit (atom nil)
          hae-urakan-maksuerat (fn [ur] (go
                                      (reset! maksuerarivit (sort-by :tyyppi (<! (maksuerat/hae-urakan-maksuerat (:id ur)))))
                                      (reset! lahetyksessa (into #{} (mapv ; Lisää lahetyksessa-settiin lähetyksessä olevat maksueränumerot
                                                                         (fn [rivi] (:numero rivi))
                                                                         (filter
                                                                             (fn [rivi]
                                                                                 (= (:tila rivi) "odottaa_vastausta"))
                                                                             @maksuerarivit))))))
          laheta-maksuerat (fn [maksueranumerot] ; Lähetä vain ne numerot, jotka eivät jo ole lähetyksessä
                               (let [lahetettavat-maksueranumerot (into #{} (filter #(not (contains? @lahetyksessa %)) maksueranumerot))]
                                      (go (reset! lahetyksessa (into #{} (clojure.set/union @lahetyksessa lahetettavat-maksueranumerot)))
                                          (let [res (<! (maksuerat/laheta-maksuerat lahetettavat-maksueranumerot))]
                                          (if res ; Poistaa lahetyksessa-setistä ne numerot, jotka lähetettiin tässä pyynnössä
                                              ;; Lähetys ok
                                              (do (reset! lahetyksessa (into #{} (remove (set lahetettavat-maksueranumerot) @lahetyksessa)))
                                                  (reset! maksuerarivit (mapv (fn [rivi]
                                                                                  (if (contains? lahetettavat-maksueranumerot (:numero rivi))
                                                                                      (assoc rivi :tila "odottaa_vastausta")
                                                                                      rivi))
                                                                              @maksuerarivit)))
                                              ;; Epäonnistui jostain syystä
                                              (do (reset! lahetyksessa (into #{} (remove (set lahetettavat-maksueranumerot) @lahetyksessa)))
                                                  (reset! maksuerarivit (mapv (fn [rivi]
                                                                                  (if (contains? lahetettavat-maksueranumerot (:numero rivi))
                                                                                      (assoc rivi :tila "virhe")
                                                                                      rivi))
                                                                              @maksuerarivit))))))))]
        (hae-urakan-maksuerat ur) ; FIXME Urakan maksuerät ja tilat haetaan kannasta kun sivulle tullaan. Entä jos tilat muuttuvat sivulla oltaessa?
        (komp/luo
            {:component-will-receive-props
             (fn [_ & [_ ur]]
                 (log "UUSI URAKKA: " (pr-str (dissoc ur :alue)))
                 (hae-urakan-maksuerat ur))}
        (fn [ur]
            [:div
            [grid/grid
             {:otsikko "Maksuerät"
              :tyhja "Ei maksueriä."
              :tallenna nil}
             [{:otsikko "Numero" :nimi :numero :tyyppi :numero :leveys "10%" :pituus 16}
              {:otsikko "Nimi" :nimi :nimi :tyyppi :string :leveys "20%" :pituus 16}
              {:otsikko "Tyyppi" :nimi :tyyppi :tyyppi :string :leveys "17%" :pituus 16}
              {:otsikko "Maksuerän summa" :nimi :maksueran-summa :tyyppi :numero :leveys "14%" :pituus 16}
              {:otsikko "Kust.suunnitelman summa" :nimi :kustannussuunnitelma-summa :tyyppi :numero :leveys "18%"}
              {:otsikko "Tila" :nimi :tila :tyyppi :string :fmt #(case %
                                                                    "odottaa_vastausta" "Lähetetty, odottaa vastausta" ; TODO Keltainen väri
                                                                    "lahetetty" "Lähetetty, kuittaus saatu" ; TODO Vihreä väri
                                                                    "virhe" "Lähetys epäonnistui!"; TODO Punainen väri
                                                                    "Ei lähetetty") :leveys "14%"}
              {:otsikko "Lähetys Sampoon" :nimi :laheta :tyyppi :nappi :nappi-nimi "Lähetä" :nappi-toiminto (fn [rivi] (laheta-maksuerat #{(:numero rivi)})) :nappi-luokka (fn [rivi] (str "nappi-ensisijainen " (if (contains? @lahetyksessa (:numero rivi)) "disabled"))) :leveys "10%"}] ;
              @maksuerarivit
             ]

          [:button.nappi-ensisijainen {:class (if (= (count @lahetyksessa) (count @maksuerarivit)) "disabled" "")
                                       :on-click #(do (.preventDefault %)
                                                      (laheta-maksuerat (into #{} (mapv (fn [rivi] (:numero rivi)) @maksuerarivit))))} "Lähetä kaikki" ]]))))

