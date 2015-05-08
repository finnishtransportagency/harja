(ns harja.views.urakka.siltatarkastukset
  "Urakan 'Siltatarkastukset' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.siltatarkastukset :as siltatarkastukset]
            [harja.tiedot.istunto :as istunto]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


;; Tällä hetkellä valittu toteuma
(defonce valittu-silta (atom nil))

(defn sillat [ur]
  (let [urakan-sillat (atom nil)
        urakka (atom nil)
        aseta-urakka (fn [ur]
                       (reset! urakka ur))]
    (aseta-urakka ur)
    (run! (let [urakka-id (:id @urakka)]
            (when urakka-id
              (go (reset! urakan-sillat (<! (siltatarkastukset/hae-urakan-sillat urakka-id)))))))

    (log "URAKAN SILLAT: " (pr-str (dissoc ur :alue)))
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ ur]]
         (log "UUSI URAKKA: " (pr-str (dissoc ur :alue)))
         (aseta-urakka ur))}

      (fn [ur]
        [:div.sillat
         [grid/grid
          {:otsikko        "Sillat"
           :tyhja          (if (nil? @urakan-sillat) [ajax-loader "Urakan alueella olevia siltoja haetaan..."] "Urakan alueella ei ole siltoja.")
           :rivi-klikattu #(reset! valittu-silta %)
           }

          ;; sarakkeet
          [{:otsikko "Silta" :nimi :siltanimi :leveys "40%"}
           {:otsikko "Siltanumero" :nimi :siltanro :leveys "10%"}
           {:otsikko "Edellinen tarkastus" :nimi :uusin_aika :tyyppi :pvm :fmt #(if % (pvm/pvm %)) :leveys "20%"}
           {:otsikko "Tarkastaja" :nimi :tarkastaja :leveys "30%"}]

          @urakan-sillat
          ]]))))

(defn sillan-tarkastukset [ur]
  (let [sillan-tarkastukset (atom nil)
        tarkastuskohteet (atom nil)
        tallennus-kaynnissa (atom false)
        urakka (atom nil)
        aseta-urakka (fn [ur]
                       (reset! urakka ur))]
    (aseta-urakka ur)
    (run! (let [urakka-id (:id @urakka)]
            (when urakka-id
              (go (reset! sillan-tarkastukset (<! (siltatarkastukset/hae-sillan-tarkastukset (:id @valittu-silta)))))
              ;; FIXME: iteroi läpi kaikki sillan-tarkastukset ja hae niille tarkastuskohteet talteen
              (go (reset! tarkastuskohteet (<! (siltatarkastukset/hae-siltatarkastuksen-kohteet (:id (first @sillan-tarkastukset)))))))))

    (log "sillan-tarkastukset" (pr-str @sillan-tarkastukset))
    (log "sillan tarkastuskohteet" (pr-str @tarkastuskohteet))
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ ur]]
         (log "sillan-tarkastukset sai propertyjä, urakka: " (pr-str (dissoc ur :alue))))}

      (fn [ur]
        [:div.sillat
         [:div
          [:span "Silta: "]
          [:span (:siltanimi @valittu-silta)]]
         ;; FIXME: gridi ei läheskään valmis vielä. Käsiteltävä sillan-tarkastukset ja niiden kaikki tarkastuskohteet...
         [grid/grid
          {:otsikko        "Sillan tarkastukset"
           :tyhja          (if (nil? @sillan-tarkastukset) [ajax-loader "Urakan alueella olevia siltoja haetaan..."] "Sillasta ei ole vielä tarkastuksia Harjassa.")
           :tunniste       :id
           }

          ;; sarakkeet
          [{:otsikko "Kohde" :nimi :siltanimi :leveys "40%"}
           {:otsikko "Tulos" :nimi :siltanro :leveys "10%"}
           {:otsikko "Lisätietoa" :nimi :uusin_aika :tyyppi :pvm :fmt #(if % (pvm/pvm %)) :leveys "20%"}]

          @sillan-tarkastukset
          ]]))))

(defn siltatarkastukset [ur]
  (if-let [vs @valittu-silta]
    [sillan-tarkastukset ur]
    [sillat ur]))
