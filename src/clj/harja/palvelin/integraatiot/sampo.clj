(ns harja.palvelin.integraatiot.sampo
  (:require [hiccup.core :refer [html]]
            [harja.kyselyt.sampo :as q]
            [harja.kyselyt.konversio :as konversio])
  (:import (java.text SimpleDateFormat)
           (java.util Date Calendar)))

(defn muodosta-kulu-id []
  (clojure.string/join "" ["kulu"
                           (let [calendar (Calendar/getInstance)]
                             (.setTime calendar (Date.))
                             (.get calendar Calendar/YEAR))]))

(defn muodosta-maksueranumero [numero]
  (clojure.string/join "" ["HA" numero]))

(defn muodosta-instance-code [numero]
  (clojure.string/join "" ["AL" numero]))

(defn custom-information [values & content]
  [:CustomInformation
   (for [[key value] values]
     [:ColumnValue {:name key} value])
   content])

(defn formatoi-paivamaara [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.S") date))

(defn laske-summa [maksuera] 100)                           ;; todo: Miten lasketaan eri maksuerätyyppien summat?

(defn paattele-tyyppi [tyyppi]
  (case tyyppi
    "lisatyo" 1
    "kokonaishintainen" 2
    "yksikköhintainen" 6
    "indeksi" 7
    "bonus" 8
    "sakko" 9
    "akillinen_hoitotyo" 10
    99))

(defn muodosta-maksuera [maksuera]
  (let [{:keys [alkupvm loppupvm vastuuhenkilo talousosasto tuotepolku]} (:toimenpideinstanssi maksuera)
        {:keys [sampoid]} (:urakka maksuera)
        maksueranumero (muodosta-maksueranumero (:numero maksuera))
        kulu-id (muodosta-kulu-id)
        instance-code (muodosta-instance-code (:numero maksuera))]

    [:NikuDataBus
     [:Header {:objectType "product" :action "write" :externalSource "NIKU" :version "8.0"}]
     [:Products
      [:Product {:name                  (:nimi maksuera)
                 :financialProjectClass "INVCLASS"
                 :start                 (formatoi-paivamaara alkupvm)
                 :finish                (formatoi-paivamaara loppupvm)
                 :financialWipClass     "WIPCLASS"
                 :financialDepartment   talousosasto
                 :managerUserName       vastuuhenkilo
                 :objectID              maksueranumero
                 :financialLocation     "Kpito"}
       [:InvestmentAssociations
        [:Allocations
         [:ParentInvestment {:defaultAllocationPercent "1.0" :InvestmentType "project" :InvestmentID sampoid}]]]
       [:InvestmentResources
        [:Resource {:resourceID kulu-id}]]
       [:InvestmentTasks
        [:Task {:outlineLevel "1"
                :name         (:nimi maksuera)
                :taskID       "~rmw"}
         [:Assignments
          [:TaskLabor {:resourceID kulu-id}]]]]
       [:OBSAssocs {:completed "false"}
        [:OBSAssoc#LiiviKP {:unitPath tuotepolku
                            :name     "Kustannuspaikat"}]
        [:OBSAssoc#LiiviSIJ {:unitPath "/Kirjanpito"
                             :name     "Sijainti"}]
        [:OBSAssoc#tuote2013 {:unitPath tuotepolku
                              :name     "Tuoteryhma/Tuote"}]]
       (custom-information {"vv_tilaus"      (:sampoid (:sopimus maksuera))
                            "vv_inst_no"     (:numero maksuera)
                            "vv_code"        maksueranumero
                            "vv_me_type"     (paattele-tyyppi (:tyyppi maksuera))
                            "vv_type"        "me"
                            "vv_status"      "2"
                            "travel_cost_ok" "false"
                            }
                           [:instance {:parentInstanceCode maksueranumero :parentObjectCode "Product" :objectCode "vv_invoice_receipt" :instanceCode instance-code}
                            (custom-information {"code"                 instance-code
                                                 "vv_payment_date"      "FOO" ; todo: Mistä saadaan maksupäivä?(date-format me-paiva)
                                                 "vv_paym_sum"          (laske-summa maksuera)
                                                 "vv_paym_sum_currency" "EUR"
                                                 "name"                 "Laskutus- ja maksutiedot"})])]]]))




(defn laheta-maksuera [db numero]
  ;; lukitse maksuera
  ;; hae maksueran tiedot
  (let [maksueran-tiedot (konversio/alaviiva->rakenne (first (q/hae-maksuera db numero)))
        maksuera-xml (muodosta-maksuera maksueran-tiedot)])
  ;; muodosta maksueran xml-viesti
  ;; (muodosta-maksuera)

  ;; lähetä jms jonoon xml
  ;; merkitse lähetetyksi, kirjaa lähetys id, vapauta lukko
  )

