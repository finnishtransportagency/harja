(ns harja.views.urakka.kulut.kulut)

(defmacro
  lomakkeen-osio
  [otsikko & osiot]
  `(do
     [:div
      {:class #{"palsta"}}
      [:h2 ~otsikko]
      ~@osiot]))
