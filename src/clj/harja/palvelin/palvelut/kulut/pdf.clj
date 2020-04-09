(ns harja.palvelin.palvelut.kulut.pdf
  (:require [harja.tyokalut.xsl-fo :as xsl-fo]))

(def ^:private border "solid 0.1mm black")

(def ^:private borders {:border-bottom border
                        :border-top border
                        :border-left border
                        :border-right border})

(defn- taulukko [otsikko]
  [:fo:table (merge borders {:table-layout "fixed"})
   [:fo:table-column {:column-width "4cm"}]
   [:fo:table-column]
   [:fo:table-body
    (when otsikko
      [:fo:table-row borders
       [:fo:table-cell (merge borders {:number-columns-spanned 2})
        [:fo:block otsikko]]])]])

(defn- tieto [otsikko sisalto]
  [:fo:block {:margin-bottom "2mm"}
   [:fo:block {:font-weight "bold" :font-size 8} otsikko]
   [:fo:block sisalto]])

(defn kulu-pdf
  []
  (with-meta
    (xsl-fo/dokumentti
      {:margin {:left "5mm" :right "5mm" :top "5mm" :bottom "5mm"
                :body "0mm"}}

      [:fo:wrapper {:font-size 8}
       (taulukko
         [:fo:block {:text-align "center"}
          [:fo:block {:font-weight "bold"}
           [:fo:block "ILMOITUS LIIKENNETTÄ HAITTAAVASTA TYÖSTÄ"]
           [:fo:block "ITM FINLANDIN TIELIIKENNEKESKUKSEEN"]]
          [:fo:block
           "Yllättävästä häiriöstä erikseen ilmoitus puhelimitse"
           " urakoitsijan linjalle 0200 21200"]])])
    {:tiedostonimi (str "kuluminimi-"
                         "-"
                         "-"
                        ".pdf")}))