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

(defn laske-summa [maksuera]
  100)

(defn luo-summat [alkupvm loppupvm maksuera]
  [:Cost
   [:segment
    {:value  (laske-summa maksuera)
     :finish alkupvm
     :start  loppupvm}]])

(defn muodosta-kustannussuunnitelma-xml [maksuera]
  (let [{:keys [alkupvm loppupvm]} (:toimenpideinstanssi maksuera)
        maksueranumero (muodosta-maksueranumero (:numero maksuera))
        kustannussuunnitelmanumero (muodosta-kustannussuunnitelmanumero (:numero maksuera))
        ]
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
        :name           (:nimi maksuera)
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
          [:GroupingAttribute
           {:value "3110201"
            :code  "lov1_id"}]
          [:GroupingAttribute
           {:value "43021"
            :code  "role_id"}]]
         [:CustomInformation
          [:ColumnValue
           {:name "vv_vat_code"}
           "L024"]]]]
       [:CustomInformation
        [:ColumnValue
         {:name "vv_purpose"}
         "5"]]]]]))
