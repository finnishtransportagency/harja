(ns harja.ui.upotettu-raportti
  "Apureita upotettujen raporttien piirtämiseen"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.ui.napit :as napit]
            [cljs.core.async :refer [<!]]
            [harja.transit :as t]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn raportin-vientimuodot 
  "Parametrit hash-map tai useita hash-mappeja. Yhdellä hash-mapilla tehdään PDF/Excel-napit samoilla
  parametreilla, usealla hash-mapilla napeilla on omat parametrit ja lisänä avaimet 
  :kasittelija :pdf/:excel ja :otsikko, joka on napin teksti"
  ([parametrit & loput-parametrit]
   [:span.upotettu-raporttitallennus  
    (doall 
      (for [p (concat [parametrit] loput-parametrit)]
        (let [{:keys [kasittelija otsikko]} p]
          ^{:key (str "raportti-" otsikko)}
          [:form {:target "_blank" :method "POST"
                  :action ((if (= kasittelija :excel) 
                             k/excel-url 
                             k/pdf-url) :raportointi)}
           [:input {:type "hidden" :name "parametrit"
                    :value (t/clj->transit (dissoc p :otsikko :kasittelija))}]  
           [napit/tallenna (str otsikko) (constantly true) {:ikoni (ikonit/harja-icon-action-download) :type "submit" :vayla-tyyli? true :esta-prevent-default? true}]])))])
  ([parametrit]
   ;; Säädetty tallenna- napit MHU urakoille etteivät ole rumasti
   (let [napin-luokka (if (= (:nimi parametrit) :ilmoitukset-raportti) "nappi-toissijainen" nil)
         class (cond
                 (or (= (:nimi parametrit) :laskutusyhteenveto-tyomaa)
                     (= (:nimi parametrit) :laskutusyhteenveto-tuotekohtainen))
                 "upotettu-raporttitallennus-mhu"

                 (= (:nimi parametrit) :ilmoitukset-raportti)
                 "upotettu-raporttitallennus-ilmoitukset"

                 :else
                 "upotettu-raporttitallennus")]

     [:span {:class class}
      ^{:key "raporttixls"}
      [:form {:target "_blank" :method "POST"
              :action (k/excel-url :raportointi)}
       [:input {:type "hidden" :name "parametrit"
                :value (t/clj->transit parametrit)}]
       [napit/tallenna "Tallenna Excel" (constantly true) {:ikoni (ikonit/livicon-download) :luokka napin-luokka :type "submit" :vayla-tyyli? false :esta-prevent-default? true}]]
      ^{:key "raporttipdf"}
      [:form {:target "_blank" :method "POST"
              :action (k/pdf-url :raportointi)}
       [:input {:type "hidden" :name "parametrit"
                :value (t/clj->transit parametrit)}]
       [napit/tallenna "Tallenna PDF" (constantly true) {:ikoni [ikonit/livicon-download] :luokka napin-luokka :type "submit" :vayla-tyyli? false :esta-prevent-default? true}]]])))
