package com.alexanderberndt.appintegration.pipeline.context;

import com.alexanderberndt.appintegration.pipeline.task.ProcessingTask;
import com.alexanderberndt.appintegration.pipeline.valuemap.RankedAndTypedValueMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TaskContextTest {

    public static final String MY_NAMESPACE = "my-namespace";

    private RankedAndTypedValueMap processingParams;

    private TaskContext<ProcessingTask> taskContext;

    @Mock
    private GlobalContext globalContextMock;

    @Mock
    private ProcessingTask task;


    @BeforeEach
    void beforeEach() {
        this.processingParams = new RankedAndTypedValueMap();
        this.taskContext = new TaskContext<ProcessingTask>(globalContextMock, task, MY_NAMESPACE, "This is a test task-context") {
        };

        Mockito.lenient().when(globalContextMock.getProcessingParams()).thenReturn(processingParams);
    }

    @Test
    void getUnqualifiedNamespaceKey() {
        TaskContext<?>.NamespaceKey nk = taskContext.parseNamespaceKey("any-variable");
        assertEquals("my-namespace", nk.getNamespace());
        assertEquals("any-variable", nk.getKey());
    }

    @Test
    void getQualifiedNamespaceKey() {
        TaskContext<?>.NamespaceKey nk = taskContext.parseNamespaceKey("other:any-other");
        assertEquals("other", nk.getNamespace());
        assertEquals("any-other", nk.getKey());
    }

    @Test
    void getValue() {
        taskContext.setValue("test1", 100);
        assertEquals(100, taskContext.getValue("test1"));
    }

    @Test
    void getValueWithType() {
        taskContext.setValue("test1", 100);
        assertEquals(new Integer(100), taskContext.getValue("test1", Integer.class));
        Mockito.verify(globalContextMock, Mockito.never()).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());
    }

    @Test
    void getValueWithWrongType() {
        taskContext.setValue("test1", 100);
        assertNull(taskContext.getValue("test1", String.class));
        Mockito.verify(globalContextMock).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());
    }

    @Test
    void getValueWithDefault() {
        taskContext.setValue("test1", 100);
        assertEquals(new Integer(100), taskContext.getValue("test1", 0));
        assertEquals(new Integer(0), taskContext.getValue("test2", 0));
        Mockito.verify(globalContextMock, Mockito.never()).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());
    }

    @Test
    void getValueWithWrongDefaultType1() {
        taskContext.setValue("test1", 100);
        assertEquals("something", taskContext.getValue("test1", "something"));
        Mockito.verify(globalContextMock).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());
    }

    @Test
    void getValueWithWrongDefaultType2() {
        taskContext.setType("test1", Integer.class);
        assertEquals("something", taskContext.getValue("test1", "something"));
        Mockito.verify(globalContextMock).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());
    }

    @Test
    void setValue() {
        taskContext.setValue("test1", "Hello");
        assertEquals("Hello", taskContext.getValue("test1", "something"));
        Mockito.verify(globalContextMock, Mockito.never()).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());

        assertEquals(Collections.singleton("test1"), processingParams.keySet(MY_NAMESPACE));
        assertEquals("Hello", processingParams.getValue(MY_NAMESPACE, "test1"));
    }

    @Test
    void setValueWithWrongType() {
        taskContext.setType("test1", Integer.class);
        taskContext.setValue("test1", "Hello");
        Mockito.verify(globalContextMock).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());
    }

    @Test
    void setType() {
        assertNull(taskContext.getType("test1"));
        taskContext.setType("test1", Integer.class);
        assertEquals(Integer.class, taskContext.getType("test1"));

        Mockito.verify(globalContextMock, Mockito.never()).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());

        taskContext.setType("test1", String.class);
        Mockito.verify(globalContextMock, Mockito.never()).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock).addError(Mockito.anyString());
    }

    @Test
    void entrySet() {
        assertEquals(0, taskContext.keySet().size());
        taskContext.setValue("test1", "Hello");
        taskContext.setValue("test2", "World!");
        assertEquals(2, taskContext.keySet().size());
        assertTrue(taskContext.keySet().containsAll(Arrays.asList("test1", "test2")));
    }

    @Test
    void setKeyComplete() {
        taskContext.setValue("test1", "Hello");
        taskContext.setValue("test2", "World!");

        taskContext.setKeyComplete();

        assertEquals("World!", taskContext.getValue("test2"));
        taskContext.setValue("test2", "Changed value");
        assertEquals("Changed value", taskContext.getValue("test2"));


        Mockito.verify(globalContextMock, Mockito.never()).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());

        taskContext.setValue("test3", "new key");
        assertNull(taskContext.getValue("test3"));
        Mockito.verify(globalContextMock).addWarning(Mockito.anyString());
        Mockito.verify(globalContextMock, Mockito.never()).addError(Mockito.anyString());
    }
}