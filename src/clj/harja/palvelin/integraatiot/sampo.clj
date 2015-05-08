(ns harja.palvelin.integraatiot.sampo
  (:require [hiccup.core :refer [html]]))


(defn custom-information [values & content]
  [:CustomInformation
   (for [[key value] values]
     [:ColumnValue {:name key} value])
   content])

(defn muodosta-maksuera []
  [:NikuDataBus
   [:Header
    {:objectType "product", :action "write", :externalSource "NIKU", :version "8.0"}]
   [:Products
    [:Product
     {:name "Kokonaishintaiset työt 2015/11 (LA205081)", :financialProjectClass "INVCLASS", :start "2015-01-01T00:00:00.000", :finish "2015-12-31T01:01:00.000", :financialWipClass "WIPCLASS", :financialDepartment "KP911303", :managerUserName "A009717", :objectID "LA205081", :financialLocation "Kpito"}
     [:InvestmentAssociations
      [:Allocations
       [:ParentInvestment
        {:defaultAllocationPercent "1.0", :InvestmentType "project", :InvestmentID "PR00020606"}]]]
     [:InvestmentResources
      [:Resource
       {:resourceID "kulu2015"}]]
     [:InvestmentTasks
      [:Task
       {:outlineLevel "1", :name "Kokonaishintaiset työt 2015/11 (LA205081)", :taskID "~rmw"}
       [:Assignments
        [:TaskLabor
         {:resourceID "kulu2015"}]]]]
     [:OBSAssocs
      {:completed "false"}
      [:OBSAssoc#LiiviKP
       {:unitPath "/Päivittäinen kunnossapito/Hoito", :name "Kustannuspaikat"}]
      [:OBSAssoc#LiiviSIJ
       {:unitPath "/Kirjanpito", :name "Sijainti"}]
      [:OBSAssoc#tuote2013
       {:unitPath "/Päivittäinen kunnossapito/Hoito", :name "Tuoteryhma/Tuote"}]]
   (custom-information {"vv_tilaus"      "00LZM-0033600"
                        "vv_inst_no"     "205081"
                        "vv_code"        "LA205081"
                        "vv_me_type"     "2"
                        "vv_type"        "me"
                        "vv_status"      "2"
                        "travel_cost_ok" "false"
                        }
                       [:instance
                        {:parentInstanceCode "LA205081", :parentObjectCode "Product", :objectCode "vv_invoice_receipt", :instanceCode "AL205081"}
                        (custom-information {"code"                 "AL205081"
                                             "vv_payment_date"      "FOO" ; (date-format me-paiva)
                                             "vv_paym_sum"          123
                                             "vv_paym_sum_currency" "EUR"
                                             "name"                 "Laskutus- ja maksutiedot"})])] ] ] )

(defn laheta-maksuera [id]
  ;; lukitse maksuera
  ;; hae maksueran tiedot
  ;; muodosta maksueran xml-viesti
  (muodosta-maksuera)

  ;; lähetä jms jonoon xml
  ;; merkitse lähetetyksi, kirjaa lähetys id, vapauta lukko
  )

