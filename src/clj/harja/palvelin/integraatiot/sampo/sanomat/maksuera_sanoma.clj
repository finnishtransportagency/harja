(ns harja.palvelin.integraatiot.sampo.sanomat.maksuera_sanoma
  (:require [hiccup.core :refer [html]]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.util Date Calendar)))

(def +xsd-polku+ "xsd/sampo/outbound/")

(defn muodosta-kulu-id []
  (str/join "" ["kulu"
                (let [calendar (Calendar/getInstance)]
                  (.setTime calendar (Date.))
                  (.get calendar Calendar/YEAR))]))

(defn maksueranumero [numero]
  (str/join "" ["HA" numero]))

(defn instance-code [numero]
  (str/join "" ["AL" numero]))

(defn custom-information [values & content]
  [:CustomInformation
   (for [[key value] values]
     [:ColumnValue {:name key} value])
   content])

(defn maksueratyyppi [tyyppi]
  (case tyyppi
    "yksikkohintainen" 6
    "kokonaishintainen" 2
    "lisatyo" 6
    "indeksi" 7
    "bonus" 8
    "sakko" 9
    "akillinen-hoitotyo" 10
    99))

(defn maksuera-hiccup [maksuera]
  (let [{:keys [alkupvm loppupvm vastuuhenkilo talousosasto talousosastopolku tuotepolku sampoid]} (:toimenpideinstanssi maksuera)
        maksueranumero (maksueranumero (:numero maksuera))
        kulu-id (muodosta-kulu-id)
        instance-code (instance-code (:numero maksuera))]
    [:NikuDataBus
     [:Header {:objectType "product" :action "write" :externalSource "NIKU" :version "8.0"}]
     [:Products
      [:Product {:name                  (apply str (take 80 (or (:nimi (:maksuera maksuera)) "N/A")))
                 :financialProjectClass "INVCLASS"
                 :start                 (pvm/aika-iso8601-ilman-millisekunteja alkupvm)
                 ;; Taloushallinnon kanssa on sovittu, että maksuerän loppupäivämäärä eli viimeisen maksuerän
                 ;; päivämäärä on urakan viimeisen vuoden viimeinen päivä. Pätee kaikkiin urakkatyyppeihin.
                 ;; Maksuerä kestää saattaa kestää siis yli toimenpideinstanssin elinkaaren ja yli projektin elinkaaren. (ks. Product :finish alla)
                 :finish                (.replace (pvm/aika-iso8601-ilman-millisekunteja (pvm/vuoden-viim-pvm (pvm/vuosi loppupvm))) "00:00:00" "17:00:00")
                 :financialWipClass     "WIPCLASS"
                 :financialDepartment   talousosasto
                 :managerUserName       "L934498" ;; Aina sama käyttäjä. SAMPO:n määritys. HAR-8804. Vastuuhenkilötietoa ei lähetetä Sampoon takaisin.
                 :objectID              maksueranumero
                 :financialLocation     "Kpito"}
       [:InvestmentAssociations
        [:Allocations
         [:ParentInvestment {:defaultAllocationPercent "1.0"
                             :InvestmentType           "project"
                             :InvestmentID             sampoid}]]]
       [:InvestmentResources
        [:Resource {:resourceID kulu-id}]]
       [:InvestmentTasks
        [:Task {:outlineLevel "1"
                :name         (:nimi (:maksuera maksuera))
                :taskID       "~rmw"}
         [:Assignments
          [:TaskLabor {:resourceID kulu-id}]]]]
       [:OBSAssocs {:completed "false"}
        [:OBSAssoc#LiiviKP {:unitPath talousosastopolku
                            :name     "Kustannuspaikat"}]
        [:OBSAssoc#LiiviSIJ {:unitPath "/Kirjanpito"
                             :name     "Sijainti"}]
        [:OBSAssoc#tuote2013 {:unitPath tuotepolku
                              :name     "Tuoteryhma/Tuote"}]]
       (custom-information {    "vv_tilaus"      (:sampoid (:sopimus maksuera))
                                "vv_inst_no"     (:numero maksuera)
                                "vv_code"        maksueranumero
                                "vv_me_type"     (maksueratyyppi (:tyyppi (:maksuera maksuera)))
                                "vv_type"        "me"
                                "vv_status"      "2"
                                "travel_cost_ok" "false"}
                           [:instance {:parentInstanceCode maksueranumero
                                           :parentObjectCode   "Product"
                                           :objectCode         "vv_invoice_receipt"
                                           :instanceCode       instance-code}
                                (custom-information {"code"                 instance-code
                                                         "vv_payment_date"      (pvm/aika-iso8601-ilman-millisekunteja (Date.))
                                                         "vv_paym_sum"          (:summa (:maksuera maksuera))
                                                         "vv_paym_sum_currency" "EUR"
                                                         "name"                 "Laskutus- ja maksutiedot"})])]]]))

(defn maksuera-xml [maksuera]
  (let[xml (xml/tee-xml-sanoma (maksuera-hiccup maksuera))]
    (if (xml/validi-xml? +xsd-polku+ "nikuxog_product.xsd" xml)
      xml
      (let [virheviesti (format "Maksuerää ei voida lähettää. Maksuerä XML ei ole validi. XML: %s" xml)]
        (log/error virheviesti)
        (throw+ {:type virheet/+invalidi-xml+
                 :virheet [{:koodi :invalidi-maksuera-xml :viesti virheviesti}]})))))



