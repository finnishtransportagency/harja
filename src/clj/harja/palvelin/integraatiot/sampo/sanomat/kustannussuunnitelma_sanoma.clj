(ns harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma
  (:require [hiccup.core :refer [html]]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.periodic :as time-period]
            [clj-time.coerce :as coerce]
            [harja.tyokalut.xml :as xml])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn muodosta-maksueranumero [numero]
  (str/join "" ["HA" numero]))

(defn muodosta-kustannussuunnitelmanumero [numero]
  (str/join "" ["AK" numero]))

(defn aikavali
  [alku loppu askel]
  (let [vali (time-period/periodic-seq alku askel)
        valilla? (fn [aika] (time/within? (time/interval alku loppu) aika))]
    (take-while valilla? vali)))

(defn luo-summat [vuosisummat]
  (mapv (fn [vuosisumma]
          [:segment
           {:value (:summa vuosisumma)
            :finish (xml/formatoi-aikaleima (:loppupvm vuosisumma))
            :start (xml/formatoi-aikaleima (:alkupvm vuosisumma))}])
        vuosisummat))

(defn muodosta-custom-information [nimi arvo]
  [:CustomInformation
   [:ColumnValue
    {:name nimi}
    arvo]])

(defn muodosta-grouping-attribute [koodi arvo]
  [:GroupingAttribute
   {:value arvo
    :code koodi}])

(defn tee-kustannussuunnitelmajakso [pvm]
  (let [vuosi (time/year (coerce/from-sql-date pvm))]
    (str "1.1." vuosi "-31.12." vuosi)))

(defn muodosta [maksuera]
  (let [{:keys [alkupvm loppupvm]} (:toimenpideinstanssi maksuera)
        maksueranumero (muodosta-maksueranumero (:numero maksuera))
        kustannussuunnitelmanumero (muodosta-kustannussuunnitelmanumero (:numero maksuera))]
    [:NikuDataBus
     [:Header
      {:objectType "costPlan"
       :action "write"
       :externalSource "NIKU"
       :version "13.1.0.0248"}]
     [:CostPlans
      [:CostPlan
       {:finishPeriod (tee-kustannussuunnitelmajakso loppupvm)
        :startPeriod (tee-kustannussuunnitelmajakso alkupvm)
        :periodType "ANNUALLY"
        :investmentType "PRODUCT"
        :investmentCode maksueranumero
        :name (apply str (take 80 (:nimi (:maksuera maksuera))))
        :code kustannussuunnitelmanumero
        :isPlanOfRecord "true"}
       [:Description ""]
       [:GroupingAttributes
        [:GroupingAttribute "role_id"]
        [:GroupingAttribute "lov1_id"]]
       [:Details
        [:Detail
         (reduce conj [:Cost] (luo-summat (:vuosittaiset-summat maksuera)))
         [:GroupingAttributes
          (muodosta-grouping-attribute "lov1_id" "3110201")
          (muodosta-grouping-attribute "role_id" (:lkp-tilinumero maksuera))]
         (muodosta-custom-information "vv_vat_code" "L024")]]
       (muodosta-custom-information "vv_purpose" "5")]]]))
