(ns harja-laadunseuranta.test-macros)

(defmacro prepare-component-tests []
  `(cljs.test/use-fixtures :each
     {:before (fn [] (reset! harja-laadunseuranta.testutils/*test-container* (cljs-react-test.utils/new-container!)))
      :after (fn [] (cljs-react-test.utils/unmount! @harja-laadunseuranta.testutils/*test-container*))}))

(defmacro with-component [component & body]
  `(do
     (reagent.core/render ~component @harja-laadunseuranta.testutils/*test-container*)
     ~@body))
