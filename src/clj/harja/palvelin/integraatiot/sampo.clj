(ns harja.palvelin.integraatiot.sampo
  (:require [hiccup.core :refer [html]])
  (:import (java.text SimpleDateFormat)
           (java.util Date Calendar)))

(defn muodosta-kulu-id []
  (clojure.string/join "" ["kulu"
                           (let [calendar (Calendar/getInstance)]
                             (.setTime calendar (Date.))
                             (.get calendar Calendar/YEAR))]))

(defn custom-information [values & content]
  [:CustomInformation
   (for [[key value] values]
     [:ColumnValue {:name key} value])
   content])

(defn muodosta-maksueranumero [numero]
  (clojure.string/join "" ["HA" numero]))

(defn formatoi-paivamaara [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.S") date))

(defn muodosta-maksuera [maksuera]
  (let [{:keys [alkupvm loppupvm vastuuhenkilo_id talousosasto_id tuotepolku]} (:toimenpideinstanssi maksuera)
        {:keys [sampoid]} (:urakka maksuera)
        maksueranumero (muodosta-maksueranumero (:numero maksuera))]

    [:NikuDataBus
     [:Header {:objectType "product" :action "write" :externalSource "NIKU" :version "8.0"}]
     [:Products
      [:Product {:name                  (:nimi maksuera)
                 :financialProjectClass "INVCLASS"
                 :start                 (formatoi-paivamaara alkupvm)
                 :finish                (formatoi-paivamaara loppupvm)
                 :financialWipClass     "WIPCLASS"
                 :financialDepartment   talousosasto_id
                 :managerUserName       vastuuhenkilo_id
                 :objectID              maksueranumero
                 :financialLocation     "Kpito"}
       [:InvestmentAssociations
        [:Allocations
         [:ParentInvestment {:defaultAllocationPercent "1.0" :InvestmentType "project" :InvestmentID sampoid}]]]
       [:InvestmentResources
        [:Resource {:resourceID (muodosta-kulu-id)}]]
       [:InvestmentTasks
        [:Task {:outlineLevel "1"
                :name         (:nimi maksuera)
                :taskID       "~rmw"}
         [:Assignments
          [:TaskLabor {:resourceID (muodosta-kulu-id)}]]]]
       [:OBSAssocs {:completed "false"}
        [:OBSAssoc#LiiviKP {:unitPath tuotepolku
                            :name     "Kustannuspaikat"}]
        [:OBSAssoc#LiiviSIJ {:unitPath "/Kirjanpito"
                             :name     "Sijainti"}]
        [:OBSAssoc#tuote2013 {:unitPath tuotepolku
                              :name     "Tuoteryhma/Tuote"}]]
       (custom-information {"vv_tilaus"      "00LZM-0033600"
                            "vv_inst_no"     "205081"
                            "vv_code"        "LA205081"
                            "vv_me_type"     "2"
                            "vv_type"        "me"
                            "vv_status"      "2"
                            "travel_cost_ok" "false"
                            }
                           [:instance {:parentInstanceCode "LA205081" :parentObjectCode "Product" :objectCode "vv_invoice_receipt" :instanceCode "AL205081"}
                            (custom-information {"code"                 "AL205081"
                                                 "vv_payment_date"      "FOO" ; (date-format me-paiva)
                                                 "vv_paym_sum"          123
                                                 "vv_paym_sum_currency" "EUR"
                                                 "name"                 "Laskutus- ja maksutiedot"})])]]]))

(defn laheta-maksuera [id]
  ;; lukitse maksuera
  ;; hae maksueran tiedot
  ;; muodosta maksueran xml-viesti
  ;; (muodosta-maksuera)

  ;; lähetä jms jonoon xml
  ;; merkitse lähetetyksi, kirjaa lähetys id, vapauta lukko
  )

