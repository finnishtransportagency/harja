(ns harja.palvelin.integraatiot.sampo.kustannussuunnitelma
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [clojure.string :as str])
  (:import (java.text SimpleDateFormat)))

(defn formatoi-paivamaara [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.S") date))

(defn muodosta-maksueranumero [numero]
  (str/join "" ["HA" numero]))

(defn muodosta-kustannussuunnitelmanumero [numero]
  (str/join "" ["AK" numero]))

(defn luo-summat [alkupvm loppupvm maksuera]
  [:Cost
   [:segment
    {:value  (:summa (:maksuera maksuera))
     :finish alkupvm
     :start  loppupvm}]])

(defn muodosta-custom-information [nimi arvo]
  [:CustomInformation
   [:ColumnValue
    {:name nimi}
    arvo]])

(defn muodosta-grouping-attribute [koodi arvo]
  [:GroupingAttribute
   {:value arvo
    :code  koodi}])

(defn valitse-lkp-tilinumero [toimenpidekoodi, tuotenumero]
  (if (or (= toimenpidekoodi "20112") (= toimenpidekoodi "20143") (= toimenpidekoodi "20179"))
    "43021"
    ; Hoitotuotteet 110 - 150, 536
    (if (or (and (>= tuotenumero 110) (<= tuotenumero 150))
            (= tuotenumero 536)
            (= tuotenumero 31))
      "43021"
      ; Ostotuotteet: 210, 240-271 ja 310-321
      (if (or (= tuotenumero 21)
              (= tuotenumero 30)
              (= tuotenumero 210)
              (and (>= tuotenumero 240) (<= tuotenumero 271))
              (and (>= tuotenumero 310) (<= tuotenumero 321)))
        "12981"
        (let [viesti (format "Toimenpidekoodilla '%1$s' ja tuonenumerolla '%2$s' ei voida päätellä LKP-tilinnumeroa kustannussuunnitelmalle", toimenpidekoodi tuotenumero)]
          (log/error viesti)
          (throw (RuntimeException. viesti)))))))

(defn muodosta-kustannussuunnitelma-xml [maksuera]
  (let [{:keys [alkupvm loppupvm]} (:toimenpideinstanssi maksuera)
        {:keys [koodi]} (:toimenpidekoodi (:toimenpideinstanssi maksuera))
        tuotenumero (:tuotenumero maksuera)
        maksueranumero (muodosta-maksueranumero (:numero maksuera))
        kustannussuunnitelmanumero (muodosta-kustannussuunnitelmanumero (:numero maksuera))]
    [:NikuDataBus
     [:Header
      {:objectType     "costPlan"
       :action         "write"
       :externalSource "NIKU"
       :version        "13.1.0.0248"}]
     [:CostPlans
      [:CostPlan
       {:finishPeriod   (formatoi-paivamaara loppupvm)
        :startPeriod    (formatoi-paivamaara alkupvm)
        :periodType     "ANNUALLY"
        :investmentType "PRODUCT"
        :investmentCode maksueranumero
        :name           (:nimi (:maksuera maksuera))
        :code           kustannussuunnitelmanumero
        :isPlanOfRecord "true"}
       [:Description ""]
       [:GroupingAttributes
        [:GroupingAttribute "role_id"]
        [:GroupingAttribute "lov1_id"]]
       [:Details
        [:Detail
         (luo-summat (formatoi-paivamaara alkupvm) (formatoi-paivamaara loppupvm) maksuera)
         [:GroupingAttributes
          (muodosta-grouping-attribute "lov1_id" "3110201")
          (muodosta-grouping-attribute "role_id" (valitse-lkp-tilinumero koodi tuotenumero))]
         (muodosta-custom-information "vv_vat_code" "L024")]]
       (muodosta-custom-information "vv_purpose" "5")]]]))
