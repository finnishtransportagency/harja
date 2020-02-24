(ns harja.views.urakka.kulut)

(defmacro
  lomakkeen-osio
  [otsikko & osiot]
  `(do
     [:div
      {:class #{"col-xs-12" "col-sm-6"}}
      [:h2 ~otsikko]
      ~@osiot]))