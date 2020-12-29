(ns harja.tyokalut.vieritys)

#_(defmacro vieritettava-osio
  [{:keys [menukomponentti] :as _optiot} & osiot]
  (let [avaimet# (filter keyword? osiot)
        valikko# ~menukomponentti
        alkuvalikko# (or valikko#
                        `(fn [avaimet]
                           [:div "valikko"
                            (for [a avaimet]
                              [:span {:on-click (harja.tyokalut.vieritys/vierita a)}
                               (name a)])]))
        ;luo-osiot (comp (map (harja.tyokalut.vieritys/tee-majakat)))
        pohja `[:<>
                [harja.tyokalut.vieritys/majakka ::top]
                [alkuvalikko# avaimet#]]]
    [:<>
     (into pohja osiot)]))

#_(defmacro vieritettava-osio
    "For convenience - luo vieritettävän osion majakoineen"
    [& osiot]
    ; keyword -> hiccup -> keyword -> hiccup
    )

#_(defn- tee-majakat
  [e]
  (if (keyword? e)
    [:<>
     [:span {:on-click (vierita ::top)} "alkuun"]
     [majakka e]]
    e))

#_(defmacro vieritettava-osio [optiot & osiot]
  (let [avaimet# (filter keyword? osiot)
        valikko# (:menukomponentti optiot)
        menukomponentti# (or valikko#
                             `(fn [avaimet]
                                [:div "valikko"]))
        pohja# [:<>
                ['harja.tyokalut.vieritys/majakka ::top]
                [menukomponentti# avaimet#]]]
    (into pohja# avaimet#)))

#_(defmacro vieritettava-osio [optiot & osiot]
  (let [avaimet# (filter keyword? osiot)
        valikko# (:menukomponentti optiot)
        menukomponentti# (or valikko#
                             `(fn [avaimet#]
                                [:div "valikko"
                                 (for [a# avaimet#]
                                   [:span {:on-click ('harja.tyokalut.vieritys/vierita a#)}
                                    (name a#)])]))
        pohja# [:<>
                ['harja.tyokalut.vieritys/majakka ::top]
                [menukomponentti# avaimet#]]
        xform# (comp (map (fn [e]
                 (if (keyword? e)
                   `[:<>
                     [:span {:on-click #('harja.tyokalut.vieritys/vierita ::top)} "alkuun"]
                     ['harja.tyokalut.vieritys/majakka e#]]
                   e))))]
    (into pohja# xform# osiot)))
