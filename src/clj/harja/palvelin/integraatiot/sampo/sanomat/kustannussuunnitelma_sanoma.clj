(ns harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma
  (:require [hiccup.core :refer [html]]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.periodic :as time-period]
            [clj-time.coerce :as coerce]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def +xsd-polku+ "xsd/sampo/outbound/")

(defn maksueranumero [numero]
  (str/join "" ["HA" numero]))

(defn kustannussuunnitelmanumero [numero]
  (str/join "" ["AK" numero]))

(defn aikavali
  [alku loppu askel]
  (let [vali (time-period/periodic-seq alku askel)
        valilla? (fn [aika] (time/within? (time/interval alku loppu) aika))]
    (take-while valilla? vali)))

(defn summat [vuosisummat]
  (mapv (fn [vuosisumma]
          [:segment
           {:value (:summa vuosisumma)
            :finish (:loppupvm vuosisumma)
            :start (:alkupvm vuosisumma)}])
        vuosisummat))

(defn custom-information [nimi arvo]
  [:CustomInformation
   [:ColumnValue
    {:name nimi}
    arvo]])

(defn grouping-attribute [koodi arvo]
  [:GroupingAttribute
   {:value arvo
    :code koodi}])

(defn kustannussuunnitelmajakso [pvm]
  (let [vuosi (time/year (coerce/from-sql-date pvm))]
    (str "1.1." vuosi "-31.12." vuosi)))

(defn kustannussuunnitelma-hiccup [maksuera]
  (let [{:keys [alkupvm loppupvm]} (:toimenpideinstanssi maksuera)
        maksueranumero (maksueranumero (:numero maksuera))
        kustannussuunnitelmanumero (kustannussuunnitelmanumero (:numero maksuera))]
    [:NikuDataBus
     [:Header
      {:objectType "costPlan"
       :action "write"
       :externalSource "NIKU"
       :version "13.1.0.0248"}]
     [:CostPlans
      [:CostPlan
       {:finishPeriod (kustannussuunnitelmajakso loppupvm)
        :startPeriod (kustannussuunnitelmajakso alkupvm)
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
         (reduce conj [:Cost] (summat (:vuosittaiset-summat maksuera)))
         [:GroupingAttributes
          (grouping-attribute "lov1_id" "3110201")
          (grouping-attribute "role_id" (:lkp-tilinumero maksuera))]
         (custom-information "vv_vat_code" "L024")]]
       (custom-information "vv_purpose" "5")]]]))

(defn kustannussuunnitelma-xml [maksuera]
  (let[xml (xml/tee-xml-sanoma (kustannussuunnitelma-hiccup maksuera))]
    (if (xml/validi-xml? +xsd-polku+ "nikuxog_costPlan.xsd" xml)
      xml
      (let [virheviesti (format "Kustannussuunnitelmaa ei voida lähettää. Kustannussuunnitelma XML ei ole validi. XML: %s"
                    xml)]
        (log/error virheviesti)
        (throw+ {:type virheet/+invalidi-xml+
                 :virheet [{:koodi :invalidi-kustannussuunnitelma-xml :viesti virheviesti}]})))))
